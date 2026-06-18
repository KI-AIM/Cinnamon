import os
import re
import time
from dataclasses import dataclass
from typing import Any, Dict, List, Optional
from urllib.parse import urlparse

import requests

from synthetic_tabular_data_generator.llm.prompt_logger import PromptLogger


DEFAULT_OPENAI_SYSTEM_PROMPT = "Return only the requested JSON or text content with no extra formatting."
LLM_PROFILE_IDS_ENV_VAR = "CINNAMON_LLM_PROFILE_IDS"
LLM_PROFILE_VAR_PREFIX = "CINNAMON_LLM_PROFILE_"
RETRYABLE_STATUS_CODES = {429, 500, 502, 503, 504}


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


def _resolve_value(
    *,
    field_name: str,
    selected_profile: Dict[str, Any],
    config_sections: List[Dict[str, Any]],
    section_names: List[str],
    default: Any = None,
    required: bool = False,
) -> Any:
    if field_name in selected_profile:
        return selected_profile[field_name]

    for section in config_sections:
        value = _first_non_empty(section.get(field_name))
        if value is not None:
            return value

    if default is not None:
        return default

    if required:
        joined_sections = ", ".join(section_names)
        raise ValueError(
            f"Missing LLM configuration '{field_name}'. Provide it in the selected llm_profile "
            f"or in one of: {joined_sections}."
        )

    return None


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
    available = ", ".join(sorted(llm_profiles.keys())) or "none"
    if not selected_profile_name:
        raise ValueError(
            "Missing llm_profile selection. "
            f"Choose one of the configured profiles: {available}."
        )
    if selected_profile_name and selected_profile_name not in llm_profiles:
        raise ValueError(
            f"Unknown llm_profile '{selected_profile_name}'. Available profiles: {available}."
        )
    selected_profile = llm_profiles.get(selected_profile_name, {})

    provider = str(
        _resolve_value(
            field_name="provider",
            selected_profile=selected_profile,
            config_sections=[model_params],
            section_names=["synthetization_configuration.algorithm.model_parameter"],
            required=True,
        )
    ).strip().lower()

    if provider not in {"ollama", "openai_compatible"}:
        raise ValueError(
            f"Unsupported LLM provider '{provider}'. Supported values are 'ollama' and 'openai_compatible'."
        )

    model_name = str(
        _resolve_value(
            field_name="model_name",
            selected_profile=selected_profile,
            config_sections=[model_params],
            section_names=["synthetization_configuration.algorithm.model_parameter"],
            required=True,
        )
    )
    raw_base_url = str(
        _resolve_value(
            field_name="base_url",
            selected_profile=selected_profile,
            config_sections=[model_params],
            section_names=["synthetization_configuration.algorithm.model_parameter"],
            required=True,
        )
    ).rstrip("/")
    explicit_endpoint_path = _resolve_value(
        field_name="endpoint_path",
        selected_profile=selected_profile,
        config_sections=[model_params],
        section_names=["synthetization_configuration.algorithm.model_parameter"],
    )
    explicit_endpoint_path_was_provided = explicit_endpoint_path is not None
    endpoint_path = str(explicit_endpoint_path or _default_endpoint_path(provider))
    healthcheck_path = str(
        _resolve_value(
            field_name="healthcheck_path",
            selected_profile=selected_profile,
            config_sections=[model_params],
            section_names=["synthetization_configuration.algorithm.model_parameter"],
            default=_default_healthcheck_path(provider),
        )
    )
    api_key = str(
        _resolve_value(
            field_name="api_key",
            selected_profile=selected_profile,
            config_sections=[model_params],
            section_names=["synthetization_configuration.algorithm.model_parameter"],
            default="",
        )
    )
    try:
        raw_temperature = _resolve_value(
            field_name="temperature",
            selected_profile=selected_profile,
            config_sections=[sampling_params],
            section_names=["synthetization_configuration.algorithm.sampling"],
            required=True,
        )
        temperature = max(0.0, min(2.0, float(raw_temperature)))

        raw_top_p = _resolve_value(
            field_name="top_p",
            selected_profile=selected_profile,
            config_sections=[sampling_params],
            section_names=["synthetization_configuration.algorithm.sampling"],
            required=True,
        )
        top_p = max(0.0, min(1.0, float(raw_top_p)))

        raw_max_tokens = _resolve_value(
            field_name="max_tokens",
            selected_profile=selected_profile,
            config_sections=[sampling_params, model_params],
            section_names=[
                "synthetization_configuration.algorithm.sampling",
                "synthetization_configuration.algorithm.model_parameter",
            ],
            required=True,
        )
        max_tokens = max(1, int(raw_max_tokens))
    except (TypeError, ValueError) as exc:
        raise ValueError(
            "Invalid LLM decoding configuration. Provide valid numeric values for "
            "'temperature' and 'top_p' in "
            "'synthetization_configuration.algorithm.sampling' and provide "
            "'max_tokens' either in 'synthetization_configuration.algorithm.sampling', "
            "'synthetization_configuration.algorithm.model_parameter', or the selected llm_profile."
        ) from exc
    timeout_seconds = max(
        1,
        int(
            _resolve_value(
                field_name="timeout_seconds",
                selected_profile=selected_profile,
                config_sections=[fitting_params, model_params],
                section_names=[
                    "synthetization_configuration.algorithm.model_fitting",
                    "synthetization_configuration.algorithm.model_parameter",
                ],
                required=True,
            )
        ),
    )
    max_retries = max(
        1,
        int(
            _resolve_value(
                field_name="max_retries",
                selected_profile=selected_profile,
                config_sections=[fitting_params, model_params],
                section_names=[
                    "synthetization_configuration.algorithm.model_fitting",
                    "synthetization_configuration.algorithm.model_parameter",
                ],
                required=True,
            )
        ),
    )
    verify_ssl = _parse_bool(
        _resolve_value(
            field_name="verify_ssl",
            selected_profile=selected_profile,
            config_sections=[model_params],
            section_names=["synthetization_configuration.algorithm.model_parameter"],
            default=True,
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
        self._prompt_logger: Optional[PromptLogger] = None
    
    def initialize(self) -> None:
        configured_base_url = self.base_url
        last_error: Optional[Exception] = None

        for candidate_base_url in self._candidate_base_urls(configured_base_url):
            try:
                self._healthcheck(candidate_base_url)
                self.base_url = candidate_base_url
                return
            except (requests.exceptions.RequestException, ValueError) as exc:
                last_error = exc

        if last_error is not None:
            raise last_error
        raise RuntimeError("Unable to reach the configured LLM API.")
    
    def set_session_key(self, session_key: Optional[str] = None) -> None:
        """Set the session key for prompt logging."""
        self._prompt_logger = PromptLogger.get_instance()
        self._prompt_logger.initialize(session_key)

    def generate_text(self, prompt: str, system_prompt: Optional[str] = None) -> str:
        if self.config.provider == "ollama":
            return self._generate_ollama(prompt, system_prompt=system_prompt)
        if self.config.provider == "openai_compatible":
            return self._generate_openai_compatible(prompt, system_prompt=system_prompt)
        raise ValueError(f"Unsupported LLM provider '{self.config.provider}'.")

    def _healthcheck(self, base_url: str) -> None:
        if self.config.provider == "ollama":
            response = self._request("GET", _join_url(base_url, self.config.healthcheck_path))
            body = response.json()
            available_models = self._extract_ollama_model_names(body)
            if self.config.model_name not in available_models:
                available = ", ".join(sorted(available_models)) or "none"
                raise ValueError(
                    f"Ollama model '{self.config.model_name}' is not available at {base_url}. "
                    f"Available models: {available}."
                )
            return

        healthcheck_url = _join_url(base_url, self.config.healthcheck_path)
        try:
            response = self._request("GET", healthcheck_url)
            body = response.json()
            return
        except requests.exceptions.HTTPError as exc:
            response = exc.response
            if response is not None and response.status_code in {404, 405}:
                return
            raise

    def _generate_ollama(self, prompt: str, system_prompt: Optional[str] = None) -> str:
        payload = {
            "model": self.config.model_name,
            "stream": False,
            "format": "json",
            "prompt": prompt,
            "options": {
                "num_predict": self.config.max_tokens,
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
            max_attempts=1,
        )
        body = response.json()
        content = body.get("response")
        if not isinstance(content, str) or not content.strip():
            raise ValueError("LLM response is empty or missing the 'response' field.")
        
        # Log prompt and response if logger is initialized
        if self._prompt_logger is not None:
            metadata = {
                "provider": self.config.provider,
                "model": self.config.model_name,
                "temperature": self.config.temperature,
                "top_p": self.config.top_p,
                "max_tokens": self.config.max_tokens,
            }
            self._prompt_logger.log_prompt_and_response(
                prompt=prompt,
                response=content,
                system_prompt=system_prompt,
                metadata=metadata,
            )
        
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
            max_attempts=1,
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
                # Log prompt and response if logger is initialized
                if self._prompt_logger is not None:
                    metadata = {
                        "provider": self.config.provider,
                        "model": self.config.model_name,
                        "temperature": self.config.temperature,
                        "top_p": self.config.top_p,
                        "max_tokens": self.config.max_tokens,
                    }
                    self._prompt_logger.log_prompt_and_response(
                        prompt=prompt,
                        response=extracted,
                        system_prompt=resolved_system_prompt,
                        metadata=metadata,
                    )
                return extracted

        text = first_choice.get("text")
        if isinstance(text, str) and text.strip():
            # Log prompt and response if logger is initialized
            if self._prompt_logger is not None:
                metadata = {
                    "provider": self.config.provider,
                    "model": self.config.model_name,
                    "temperature": self.config.temperature,
                    "top_p": self.config.top_p,
                    "max_tokens": self.config.max_tokens,
                }
                self._prompt_logger.log_prompt_and_response(
                    prompt=prompt,
                    response=text,
                    system_prompt=resolved_system_prompt,
                    metadata=metadata,
                )
            return text

        raise ValueError("OpenAI-compatible response is missing text content.")

    def _request(
        self,
        method: str,
        url: str,
        json: Optional[Dict[str, Any]] = None,
        *,
        max_attempts: Optional[int] = None,
    ) -> requests.Response:
        headers = {"Content-Type": "application/json"}
        if self.config.api_key:
            headers["Authorization"] = f"Bearer {self.config.api_key}"

        last_error: Optional[Exception] = None
        resolved_max_attempts = max(1, self.config.max_retries if max_attempts is None else int(max_attempts))

        for attempt in range(resolved_max_attempts):
            try:
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
            except requests.exceptions.HTTPError as exc:
                last_error = exc
                status_code = exc.response.status_code if exc.response is not None else None
                if status_code not in RETRYABLE_STATUS_CODES or attempt == resolved_max_attempts - 1:
                    raise
            except (requests.exceptions.Timeout, requests.exceptions.ConnectionError) as exc:
                last_error = exc
                if attempt == resolved_max_attempts - 1:
                    raise

            self._sleep_before_retry(attempt)

        if last_error is not None:
            raise last_error
        raise RuntimeError("LLM request failed without a captured exception.")

    @staticmethod
    def _sleep_before_retry(attempt: int) -> None:
        delay_seconds = min(2.0, 0.25 * (2 ** attempt))
        time.sleep(delay_seconds)

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
    def _extract_ollama_model_names(body: Any) -> List[str]:
        if not isinstance(body, dict):
            return []

        models = body.get("models")
        if not isinstance(models, list):
            return []

        names: List[str] = []
        for model in models:
            if not isinstance(model, dict):
                continue
            name = model.get("name")
            if isinstance(name, str) and name.strip():
                names.append(name.strip())

        return names

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
