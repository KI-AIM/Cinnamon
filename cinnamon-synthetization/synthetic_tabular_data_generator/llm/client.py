import os
import re
from dataclasses import dataclass
from typing import Any, Dict, List, Optional
from urllib.parse import urlparse

import requests


DEFAULT_OPENAI_SYSTEM_PROMPT = "Return only the requested JSON or text content with no extra formatting."
LLM_PROFILE_IDS_ENV_VAR = "CINNAMON_LLM_PROFILE_IDS"
LLM_PROFILE_VAR_PREFIX = "CINNAMON_LLM_PROFILE_"


LLM_ENV_VARS = {
    "provider": "CINNAMON_LLM_PROVIDER",
    "model_name": "CINNAMON_LLM_MODEL_NAME",
    "base_url": "CINNAMON_LLM_BASE_URL",
    "endpoint_path": "CINNAMON_LLM_ENDPOINT_PATH",
    "healthcheck_path": "CINNAMON_LLM_HEALTHCHECK_PATH",
    "api_key": "CINNAMON_LLM_API_KEY",
    "timeout_seconds": "CINNAMON_LLM_TIMEOUT_SECONDS",
    "max_retries": "CINNAMON_LLM_MAX_RETRIES",
    "verify_ssl": "CINNAMON_LLM_VERIFY_SSL",
    "temperature": "CINNAMON_LLM_TEMPERATURE",
    "top_p": "CINNAMON_LLM_TOP_P",
    "max_tokens": "CINNAMON_LLM_MAX_TOKENS",
}

LLM_PROFILE_FIELDS = {
    "provider": "PROVIDER",
    "model_name": "MODEL_NAME",
    "base_url": "BASE_URL",
    "endpoint_path": "ENDPOINT_PATH",
    "healthcheck_path": "HEALTHCHECK_PATH",
    "api_key": "API_KEY",
    "timeout_seconds": "TIMEOUT_SECONDS",
    "max_retries": "MAX_RETRIES",
    "verify_ssl": "VERIFY_SSL",
    "max_tokens": "MAX_TOKENS",
}


def _parse_bool(value: Any, default: bool) -> bool:
    if value is None:
        return default
    if isinstance(value, bool):
        return value
    if isinstance(value, (int, float)):
        return value != 0
    if isinstance(value, str):
        normalized = value.strip().lower()
        if normalized in {"1", "true", "yes", "y", "on"}:
            return True
        if normalized in {"0", "false", "no", "n", "off"}:
            return False
    return default


def _first_non_empty(*values: Any) -> Any:
    for value in values:
        if value is None:
            continue
        if isinstance(value, str) and not value.strip():
            continue
        return value
    return None


def _join_url(base_url: str, path: str) -> str:
    trimmed_base = base_url.rstrip("/")
    trimmed_path = path.strip()
    if not trimmed_path:
        return trimmed_base
    return f"{trimmed_base}/{trimmed_path.lstrip('/')}"


def _require_non_empty(config: Dict[str, Any], key: str, section_name: str) -> Any:
    value = config.get(key)
    if value is None:
        raise ValueError(
            f"Missing LLM configuration '{key}' in '{section_name}'. "
            "LLM settings must be provided via algorithm configuration."
        )
    if isinstance(value, str) and not value.strip():
        raise ValueError(
            f"Empty LLM configuration '{key}' in '{section_name}'. "
            "LLM settings must be provided via algorithm configuration."
        )
    return value


def _read_env_value(name: str) -> Optional[str]:
    value = os.getenv(name)
    if value is None:
        return None
    stripped = value.strip()
    if not stripped:
        return None
    return stripped


def _to_env_token(raw_value: str) -> str:
    normalized = re.sub(r"[^A-Za-z0-9]+", "_", raw_value.strip()).strip("_")
    return normalized.upper()


def load_llm_profiles_from_env() -> Dict[str, Dict[str, Any]]:
    raw_ids = _read_env_value(LLM_PROFILE_IDS_ENV_VAR)
    if raw_ids is None:
        return {}

    profiles: Dict[str, Dict[str, Any]] = {}
    for raw_profile_id in raw_ids.split(","):
        profile_id = raw_profile_id.strip()
        if not profile_id:
            continue

        env_token = _to_env_token(profile_id)
        profile_name = _read_env_value(f"{LLM_PROFILE_VAR_PREFIX}{env_token}_NAME") or profile_id

        profile_config: Dict[str, Any] = {}
        for field_name, field_env_suffix in LLM_PROFILE_FIELDS.items():
            value = _read_env_value(f"{LLM_PROFILE_VAR_PREFIX}{env_token}_{field_env_suffix}")
            if value is not None:
                profile_config[field_name] = value

        required = {"provider", "model_name", "base_url"}
        if not required.issubset(profile_config.keys()):
            continue

        if profile_name not in profiles:
            profiles[profile_name] = profile_config

    return profiles


def get_llm_profile_names() -> List[str]:
    return list(load_llm_profiles_from_env().keys())


def _get_env_or_config_value(
    config: Dict[str, Any],
    key: str,
    section_name: str,
    env_var_name: str,
) -> Any:
    value = _first_non_empty(_read_env_value(env_var_name), config.get(key))
    if value is None:
        raise ValueError(
            f"Missing LLM configuration '{key}'. Set environment variable '{env_var_name}' "
            f"or provide it in '{section_name}'."
        )
    return value


def _default_endpoint_path(provider: str) -> str:
    if provider == "ollama":
        return "/api/generate"
    return "/v1/chat/completions"


def _default_healthcheck_path(provider: str) -> str:
    if provider == "ollama":
        return "/api/tags"
    return "/v1/models"


@dataclass
class LlmClientConfig:
    provider: str
    model_name: str
    base_url: str
    endpoint_path: str
    healthcheck_path: str
    api_key: str
    temperature: float
    top_p: float
    max_tokens: int
    timeout_seconds: int
    max_retries: int
    verify_ssl: bool


def load_llm_client_config(algorithm_config: Dict[str, Any]) -> LlmClientConfig:
    algorithm_section = (
        algorithm_config.get("synthetization_configuration", {})
        .get("algorithm", {})
    )
    model_params = (
        algorithm_section.get("model_parameter", {})
    )
    llm_profile_params = (
        algorithm_section.get("llm_profile", {})
    )
    fitting_params = (
        algorithm_section.get("model_fitting", {})
    )
    sampling_params = (
        algorithm_section.get("sampling", {})
    )
    selected_profile_raw = _first_non_empty(
        llm_profile_params.get("llm_profile"),
        model_params.get("llm_profile"),
        "",
    )
    selected_profile_name = "" if selected_profile_raw is None else str(selected_profile_raw).strip()
    llm_profiles = load_llm_profiles_from_env()
    if selected_profile_name and selected_profile_name not in llm_profiles:
        available = ", ".join(sorted(llm_profiles.keys())) or "none"
        raise ValueError(
            f"Unknown llm_profile '{selected_profile_name}'. Available profiles: {available}."
        )
    selected_profile = llm_profiles.get(selected_profile_name, {})

    def resolve_profile_model_or_env(field_name: str) -> Any:
        if field_name in selected_profile:
            return selected_profile[field_name]
        return _get_env_or_config_value(
            model_params,
            field_name,
            "synthetization_configuration.algorithm.model_parameter",
            LLM_ENV_VARS[field_name],
        )

    def resolve_profile_fitting_or_env(field_name: str) -> Any:
        if field_name in selected_profile:
            return selected_profile[field_name]
        return _get_env_or_config_value(
            fitting_params,
            field_name,
            "synthetization_configuration.algorithm.model_fitting",
            LLM_ENV_VARS[field_name],
        )

    provider = str(resolve_profile_model_or_env("provider")).strip().lower()

    if provider not in {"ollama", "openai_compatible"}:
        raise ValueError(
            f"Unsupported LLM provider '{provider}'. Supported values are 'ollama' and 'openai_compatible'."
        )

    model_name = str(resolve_profile_model_or_env("model_name"))
    raw_base_url = str(resolve_profile_model_or_env("base_url")).rstrip("/")
    configured_endpoint_path = _first_non_empty(
        selected_profile.get("endpoint_path"),
        _read_env_value(LLM_ENV_VARS["endpoint_path"]),
        model_params.get("endpoint_path"),
    )
    explicit_endpoint_path_was_provided = configured_endpoint_path is not None
    endpoint_path = str(configured_endpoint_path or _default_endpoint_path(provider))
    healthcheck_path = str(
        _first_non_empty(
            selected_profile.get("healthcheck_path"),
            _read_env_value(LLM_ENV_VARS["healthcheck_path"]),
            model_params.get("healthcheck_path"),
            _default_healthcheck_path(provider),
        )
    )
    api_key = str(
        _first_non_empty(
            selected_profile.get("api_key"),
            _read_env_value(LLM_ENV_VARS["api_key"]),
            model_params.get("api_key"),
            "",
        )
    )
    try:
        raw_temperature = _first_non_empty(
            _read_env_value(LLM_ENV_VARS["temperature"]),
            sampling_params.get("temperature"),
        )
        if raw_temperature is None:
            raise ValueError(
                "Missing LLM configuration 'temperature'. Set environment variable "
                f"'{LLM_ENV_VARS['temperature']}' or provide it in "
                "'synthetization_configuration.algorithm.sampling'."
            )
        temperature = max(0.0, min(2.0, float(raw_temperature)))

        raw_top_p = _first_non_empty(
            _read_env_value(LLM_ENV_VARS["top_p"]),
            sampling_params.get("top_p"),
        )
        if raw_top_p is None:
            raise ValueError(
                "Missing LLM configuration 'top_p'. Set environment variable "
                f"'{LLM_ENV_VARS['top_p']}' or provide it in "
                "'synthetization_configuration.algorithm.sampling'."
            )
        top_p = max(0.0, min(1.0, float(raw_top_p)))

        raw_max_tokens = _first_non_empty(
            selected_profile.get("max_tokens"),
            _read_env_value(LLM_ENV_VARS["max_tokens"]),
        )
        if raw_max_tokens is None:
            raise ValueError(
                "Missing LLM configuration 'max_tokens'. Set environment variable "
                f"'{LLM_ENV_VARS['max_tokens']}'."
            )
        max_tokens = max(1, int(raw_max_tokens))
    except (TypeError, ValueError) as exc:
        raise ValueError(
            "Invalid LLM decoding configuration. Set valid numeric values via "
            f"'{LLM_ENV_VARS['temperature']}', '{LLM_ENV_VARS['top_p']}' "
            "(or provide them in 'synthetization_configuration.algorithm.sampling') "
            f"and set '{LLM_ENV_VARS['max_tokens']}'."
        ) from exc
    timeout_seconds = max(
        1,
        int(
            _get_env_or_config_value(
                {"timeout_seconds": resolve_profile_fitting_or_env("timeout_seconds")},
                "timeout_seconds",
                "synthetization_configuration.algorithm.model_fitting",
                LLM_ENV_VARS["timeout_seconds"],
            )
        ),
    )
    max_retries = max(
        1,
        int(
            _get_env_or_config_value(
                {"max_retries": resolve_profile_fitting_or_env("max_retries")},
                "max_retries",
                "synthetization_configuration.algorithm.model_fitting",
                LLM_ENV_VARS["max_retries"],
            )
        ),
    )
    verify_ssl = _parse_bool(
        _first_non_empty(
            selected_profile.get("verify_ssl"),
            _read_env_value(LLM_ENV_VARS["verify_ssl"]),
            model_params.get("verify_ssl"),
            True,
        ),
        default=True,
    )

    base_url, endpoint_path = _normalize_base_url_and_endpoint(
        raw_base_url,
        endpoint_path,
        explicit_endpoint_path_was_provided=explicit_endpoint_path_was_provided,
    )

    return LlmClientConfig(
        provider=provider,
        model_name=model_name,
        base_url=base_url,
        endpoint_path=endpoint_path,
        healthcheck_path=healthcheck_path,
        api_key=api_key,
        temperature=temperature,
        top_p=top_p,
        max_tokens=max_tokens,
        timeout_seconds=timeout_seconds,
        max_retries=max_retries,
        verify_ssl=verify_ssl,
    )


def _normalize_base_url_and_endpoint(
    raw_base_url: str,
    endpoint_path: str,
    explicit_endpoint_path_was_provided: bool,
) -> tuple[str, str]:
    parsed = urlparse(raw_base_url)
    parsed_path = parsed.path.rstrip("/")
    known_endpoint_paths = {"/api/generate", "/v1/chat/completions"}

    if not parsed_path:
        return raw_base_url.rstrip("/"), endpoint_path

    for known_endpoint_path in known_endpoint_paths:
        if not parsed_path.endswith(known_endpoint_path):
            continue

        if explicit_endpoint_path_was_provided and endpoint_path not in known_endpoint_paths:
            break

        base_path_prefix = parsed_path[: -len(known_endpoint_path)].rstrip("/")
        normalized_base_url = f"{parsed.scheme or 'http'}://{parsed.netloc}{base_path_prefix}".rstrip("/")
        return normalized_base_url, known_endpoint_path

    if explicit_endpoint_path_was_provided:
        return raw_base_url.rstrip("/"), endpoint_path

    normalized_base_url = f"{parsed.scheme or 'http'}://{parsed.netloc}".rstrip("/")
    return normalized_base_url, parsed_path


class LlmClient:
    def __init__(self, config: LlmClientConfig) -> None:
        self.config = config
        self.base_url = config.base_url.rstrip("/")

    def initialize(self) -> None:
        configured_base_url = self.base_url
        last_error: Optional[Exception] = None

        for candidate_base_url in self._candidate_base_urls(configured_base_url):
            try:
                self._healthcheck(candidate_base_url)
                self.base_url = candidate_base_url
                if candidate_base_url != configured_base_url:
                    print(
                        f"LLM URL fallback activated: '{configured_base_url}' was unreachable, "
                        f"using '{candidate_base_url}' instead."
                    )
                return
            except requests.exceptions.RequestException as exc:
                last_error = exc

        if last_error is not None:
            raise last_error
        raise RuntimeError("Unable to reach the configured LLM API.")

    def generate_text(self, prompt: str, system_prompt: Optional[str] = None) -> str:
        if self.config.provider == "ollama":
            return self._generate_ollama(prompt, system_prompt=system_prompt)
        if self.config.provider == "openai_compatible":
            return self._generate_openai_compatible(prompt, system_prompt=system_prompt)
        raise ValueError(f"Unsupported LLM provider '{self.config.provider}'.")

    def _healthcheck(self, base_url: str) -> None:
        if self.config.provider == "ollama":
            response = self._request("GET", _join_url(base_url, self.config.healthcheck_path))
            models = response.json().get("models", [])
            available_models = [item.get("name") for item in models if isinstance(item, dict)]
            if available_models and self.config.model_name not in available_models:
                print(
                    f"Model '{self.config.model_name}' is currently not present in the provider model list. "
                    "The provider may still load it on first request."
                )
            return

        healthcheck_url = _join_url(base_url, self.config.healthcheck_path)
        try:
            response = self._request("GET", healthcheck_url)
            body = response.json()
            if isinstance(body, dict):
                model_entries = body.get("data", [])
                available_models = [item.get("id") for item in model_entries if isinstance(item, dict)]
                if available_models and self.config.model_name not in available_models:
                    print(
                        f"Model '{self.config.model_name}' was not listed by the OpenAI-compatible server. "
                        "The provider may still accept it during generation."
                    )
            return
        except requests.exceptions.HTTPError as exc:
            response = exc.response
            if response is not None and response.status_code in {404, 405}:
                print(
                    f"LLM healthcheck path '{self.config.healthcheck_path}' is not available on '{base_url}'. "
                    "Deferring validation to the generation endpoint."
                )
                return
            raise

    def _generate_ollama(self, prompt: str, system_prompt: Optional[str] = None) -> str:
        payload = {
            "model": self.config.model_name,
            "stream": False,
            "format": "json",
            "prompt": prompt,
            "options": {
                "temperature": self.config.temperature,
                "top_p": self.config.top_p,
            },
        }
        if system_prompt is not None and system_prompt.strip():
            payload["system"] = system_prompt.strip()
        response = self._request(
            "POST",
            _join_url(self.base_url, self.config.endpoint_path),
            json=payload,
        )
        body = response.json()
        content = body.get("response")
        if not isinstance(content, str) or not content.strip():
            raise ValueError("LLM response is empty or missing the 'response' field.")
        return content

    def _generate_openai_compatible(self, prompt: str, system_prompt: Optional[str] = None) -> str:
        resolved_system_prompt = system_prompt.strip() if isinstance(system_prompt, str) and system_prompt.strip() else DEFAULT_OPENAI_SYSTEM_PROMPT
        payload = {
            "model": self.config.model_name,
            "messages": [
                {
                    "role": "system",
                    "content": resolved_system_prompt,
                },
                {
                    "role": "user",
                    "content": prompt,
                },
            ],
            "temperature": self.config.temperature,
            "top_p": self.config.top_p,
            "max_tokens": self.config.max_tokens,
        }
        response = self._request(
            "POST",
            _join_url(self.base_url, self.config.endpoint_path),
            json=payload,
        )
        body = response.json()
        choices = body.get("choices")
        if not isinstance(choices, list) or not choices:
            raise ValueError("OpenAI-compatible response is missing the 'choices' field.")

        first_choice = choices[0]
        if not isinstance(first_choice, dict):
            raise ValueError("OpenAI-compatible response contains an invalid choice entry.")

        message = first_choice.get("message")
        if isinstance(message, dict):
            content = message.get("content")
            extracted = self._normalize_openai_content(content)
            if extracted:
                return extracted

        text = first_choice.get("text")
        if isinstance(text, str) and text.strip():
            return text

        raise ValueError("OpenAI-compatible response is missing text content.")

    def _request(self, method: str, url: str, json: Optional[Dict[str, Any]] = None) -> requests.Response:
        headers = {"Content-Type": "application/json"}
        if self.config.api_key:
            headers["Authorization"] = f"Bearer {self.config.api_key}"

        response = requests.request(
            method,
            url,
            headers=headers,
            json=json,
            timeout=self.config.timeout_seconds,
            verify=self.config.verify_ssl,
        )
        response.raise_for_status()
        return response

    @staticmethod
    def _normalize_openai_content(content: Any) -> str:
        if isinstance(content, str):
            return content.strip()

        if isinstance(content, list):
            parts: List[str] = []
            for item in content:
                if isinstance(item, dict):
                    text = item.get("text")
                    if isinstance(text, str) and text.strip():
                        parts.append(text.strip())
                elif isinstance(item, str) and item.strip():
                    parts.append(item.strip())
            return "\n".join(parts).strip()

        return ""

    @staticmethod
    def _candidate_base_urls(base_url: str) -> List[str]:
        candidates = [base_url.rstrip("/")]
        parsed = urlparse(base_url)
        host = parsed.hostname

        fallback_host: Optional[str] = None
        if host in {"127.0.0.1", "localhost"}:
            fallback_host = "host.docker.internal"
        elif host == "host.docker.internal":
            fallback_host = "127.0.0.1"

        if fallback_host is not None:
            fallback_url = LlmClient._replace_url_host(base_url, fallback_host)
            if fallback_url not in candidates:
                candidates.append(fallback_url)

        return candidates

    @staticmethod
    def _replace_url_host(base_url: str, new_host: str) -> str:
        parsed = urlparse(base_url)
        scheme = parsed.scheme or "http"
        port_part = f":{parsed.port}" if parsed.port is not None else ""
        path_part = parsed.path.rstrip("/")
        return f"{scheme}://{new_host}{port_part}{path_part}"


def create_llm_client(config: LlmClientConfig) -> LlmClient:
    return LlmClient(config)
