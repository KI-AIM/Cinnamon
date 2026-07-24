from __future__ import annotations

from dataclasses import dataclass
import socket
from typing import Any, Dict, List, Optional

import requests

from synthetic_tabular_data_generator.llm.client import (
    _parse_bool,
    _read_env_value,
    _to_env_token,
)


EMBEDDING_PROFILE_IDS_ENV_VAR = "CINNAMON_EMBEDDING_PROFILE_IDS"
EMBEDDING_PROFILE_VAR_PREFIX = "CINNAMON_EMBEDDING_PROFILE_"

EMBEDDING_PROFILE_FIELDS = {
    "provider": "PROVIDER",
    "model_name": "MODEL_NAME",
    "base_url": "BASE_URL",
    "endpoint_path": "ENDPOINT_PATH",
    "healthcheck_path": "HEALTHCHECK_PATH",
    "timeout_seconds": "TIMEOUT_SECONDS",
    "max_retries": "MAX_RETRIES",
    "verify_ssl": "VERIFY_SSL",
}


@dataclass(frozen=True)
class EmbeddingProfileConfig:
    provider: str
    model_name: str
    base_url: str
    endpoint_path: str
    healthcheck_path: str
    timeout_seconds: int
    max_retries: int
    verify_ssl: bool


def load_embedding_profiles_from_env() -> Dict[str, Dict[str, Any]]:
    raw_ids = _read_env_value(EMBEDDING_PROFILE_IDS_ENV_VAR)
    if raw_ids is None:
        return {}

    profiles: Dict[str, Dict[str, Any]] = {}
    for raw_profile_id in raw_ids.split(","):
        profile_id = raw_profile_id.strip()
        if not profile_id:
            continue

        env_token = _to_env_token(profile_id)
        profile_name = _read_env_value(f"{EMBEDDING_PROFILE_VAR_PREFIX}{env_token}_NAME") or profile_id

        profile_config: Dict[str, Any] = {}
        for field_name, field_env_suffix in EMBEDDING_PROFILE_FIELDS.items():
            value = _read_env_value(f"{EMBEDDING_PROFILE_VAR_PREFIX}{env_token}_{field_env_suffix}")
            if value is not None:
                profile_config[field_name] = value

        required = {"provider"}
        if not required.issubset(profile_config.keys()):
            continue

        if profile_name not in profiles:
            profiles[profile_name] = profile_config

    return profiles


def get_embedding_profile_names() -> List[str]:
    return list(load_embedding_profiles_from_env().keys())


def load_embedding_profile_by_name(selected_profile_name: str) -> EmbeddingProfileConfig:
    profiles = load_embedding_profiles_from_env()
    available = ", ".join(sorted(profiles.keys())) or "none"
    if not selected_profile_name:
        raise ValueError(
            "Missing embedding_model selection. "
            f"Choose one of the configured embedding profiles: {available}."
        )
    if selected_profile_name not in profiles:
        raise ValueError(
            f"Unknown embedding_model '{selected_profile_name}'. Available embedding profiles: {available}."
        )

    selected_profile = profiles[selected_profile_name]
    profile_provider = str(selected_profile.get("provider", "")).strip().lower()
    if profile_provider != "ollama":
        raise ValueError(
            f"Unsupported embedding profile provider '{profile_provider}'. Only 'ollama' is supported."
        )

    model_name = str(selected_profile.get("model_name", "")).strip()
    base_url = str(selected_profile.get("base_url", "")).rstrip("/")
    if not model_name or not base_url:
        raise ValueError(
            f"Embedding profile '{selected_profile_name}' must define MODEL_NAME and BASE_URL."
        )

    endpoint_path = str(selected_profile.get("endpoint_path", "/api/embed")).strip() or "/api/embed"
    healthcheck_path = str(selected_profile.get("healthcheck_path", "/api/tags")).strip() or "/api/tags"
    timeout_seconds = int(selected_profile.get("timeout_seconds", 120))
    max_retries = int(selected_profile.get("max_retries", 3))
    verify_ssl = _parse_bool(selected_profile.get("verify_ssl"), True)

    return EmbeddingProfileConfig(
        provider=profile_provider,
        model_name=model_name,
        base_url=base_url,
        endpoint_path=endpoint_path,
        healthcheck_path=healthcheck_path,
        timeout_seconds=timeout_seconds,
        max_retries=max_retries,
        verify_ssl=verify_ssl,
    )


def load_embedding_profile_config(algorithm_config: Dict[str, Any]) -> Optional[EmbeddingProfileConfig]:
    algorithm_section = (
        algorithm_config.get("synthetization_configuration", {})
        .get("algorithm", {})
    )
    model_params = algorithm_section.get("model_parameter", {})
    provider = str(model_params.get("embedding_provider", "bm25")).strip().lower()
    if provider != "ollama":
        return None
    return load_embedding_profile_by_name(str(model_params.get("embedding_model", "")).strip())


def embed_ollama_texts(
    profile: EmbeddingProfileConfig,
    texts: List[str],
    *,
    truncate: bool = True,
) -> List[List[float]]:
    if not texts:
        return []
    base_url = profile.base_url
    if "host.docker.internal" in base_url:
        try:
            socket.getaddrinfo("host.docker.internal", 11434)
        except socket.gaierror:
            base_url = base_url.replace("host.docker.internal", "localhost")
    url = f"{base_url}{profile.endpoint_path}"
    last_error: Optional[Exception] = None
    for attempt_index in range(profile.max_retries):
        try:
            response = requests.post(
                url,
                json={"model": profile.model_name, "input": texts, "truncate": truncate},
                timeout=profile.timeout_seconds,
                verify=profile.verify_ssl,
            )
            response.raise_for_status()
            embeddings = response.json().get("embeddings")
            if not isinstance(embeddings, list) or len(embeddings) != len(texts):
                raise ValueError("Embedding response does not contain one vector per input text.")
            return [[float(value) for value in vector] for vector in embeddings]
        except Exception as exc:  # noqa: BLE001
            last_error = exc
            if attempt_index + 1 >= profile.max_retries:
                break
    raise RuntimeError("Unable to retrieve embeddings from the configured Ollama embedding profile.") from last_error
