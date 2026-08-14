from __future__ import annotations

import json
import logging
import os
from pathlib import Path

import torch
from huggingface_hub import snapshot_download

logger = logging.getLogger(__name__)

PROJECT_ROOT = Path(__file__).resolve().parents[2]

BATCH_SIZE = 16

MODEL_ENV_KEYS = {
    "XLM ROBERTA": "XLM",
    "GELECTRA": "GELECTRA",
}

def load_inference_config(model_path: str | Path) -> tuple[int, int]:
    """Loads the model-specific window size and overlap.

    Args:
        model_path: The path to the model.

    Returns:
        A tuple of the window size and overlap.
    """
    config_path = Path(model_path) / "inference_config.json"
    if not config_path.is_file():
        raise FileNotFoundError(
            f"Required inference configuration is missing: {config_path}. "
            "Provide an inference_config.json next to the model checkpoint "
            "with integer 'window_size' and 'overlap' values."
        )

    try:
        with config_path.open(encoding="utf-8") as file:
            config = json.load(file)
    except json.JSONDecodeError as error:
        raise ValueError(f"{config_path} is not valid JSON: {error.msg}") from error

    if not isinstance(config, dict):
        raise ValueError(f"{config_path} must contain a JSON object")

    missing_keys = [key for key in ("window_size", "overlap") if key not in config]
    if missing_keys:
        missing = ", ".join(repr(key) for key in missing_keys)
        raise ValueError(f"{config_path} must define {missing}")

    try:
        window_size = int(config["window_size"])
        overlap = int(config["overlap"])
    except (TypeError, ValueError) as error:
        raise ValueError(
            f"{config_path} must define integer 'window_size' and 'overlap' values"
        ) from error

    if window_size <= 0:
        raise ValueError(f"{config_path}: window_size must be positive")
    if overlap < 0 or overlap >= window_size:
        raise ValueError(
            f"{config_path}: overlap must be >= 0 and smaller than window_size"
        )
    return window_size, overlap


def resolve_device(device: str | None = "auto") -> str:
    """Resolves the device specification to a Transformers pipeline index.

    Args:
        device: Can be "auto", "cuda", "cpu" or "cuda:N".

    Returns:
        "cpu" if a cpu is specified or no cuda is available, or cuda:N
    """
    if device is None or device == "auto":
        return "cuda:0" if torch.cuda.is_available() else "cpu"

    if device == "cpu":
        return "cpu"

    if device == "cuda":
        device = "cuda:0"

    if not device.startswith("cuda:"):
        raise ValueError(f"Unsupported device: {device}")

    index = int(device.split(":", 1)[1])

    if not torch.cuda.is_available():
        raise RuntimeError(
            f"CUDA device {device} requested, but CUDA is not available."
        )

    if index < 0 or index >= torch.cuda.device_count():
        raise RuntimeError(f"CUDA device {index} is not available.")

    return device


def project_path(path: str | Path) -> Path:
    """Resolve a relative path.

    Args:
        path: A directory within the project.

    Returns:
        The `path` if it's absolute or PROJECT_ROOT / value if it's relative.
    """
    value = Path(path).expanduser()
    return value if value.is_absolute() else PROJECT_ROOT / value


def existing_model_directory(model_path: str | Path) -> Path | None:
    """Returns the local model directory, if empty returns None.

    Checks if the path exists and if config.json is inside (an indicator for a non-empty directory).

    Args:
        model_path: The local model path to be resolved.

    Returns:
        The local path to the model.
    """
    path = project_path(model_path)
    if path.is_file():
        path = path.parent
    if path.is_dir() and (path / "config.json").is_file():
        return path
    return None


def read_hugging_face_token() -> str | None:
    """Reads a token from the configured file (env HF_TOKEN_FILE).

    Returns:
        The token if the file exists else None.
    """
    token_path = Path(os.getenv("HF_TOKEN_FILE", ""))

    if token_path.is_file():
        token = token_path.read_text(encoding="utf-8").strip()
        if token:
            return token
    return None


def resolve_model_directory(model_type: str) -> Path:
    """Resolves a mounted local model or downloads it into the mounted cache.

    If a local model is absent, it's fetched from Hugging Face and stored to TEXT_ANONYMIZATION_MODEL_CACHE.

    Args:
        model_type: The type of the model, can be anything in MODEL_ENV_KEYS.

    Returns:
        The model's local path.
    """
    env_key = MODEL_ENV_KEYS[model_type]

    local_path = os.getenv(f"TEXT_ANONYMIZATION_{env_key}_LOCAL_PATH", "models")

    existing = existing_model_directory(local_path)
    if existing is not None:
        logger.info(f"Found existing model at {local_path}")
        return existing

    repository = os.getenv(f"TEXT_ANONYMIZATION_{env_key}_HF_REPOSITORY")
    if not repository:
        raise FileNotFoundError(
            f"No usable local model found for {model_type} at {local_path}, "
            f"and TEXT_ANONYMIZATION_{env_key}_HF_REPOSITORY is not configured."
        )

    cache_root = project_path(os.getenv("TEXT_ANONYMIZATION_MODEL_CACHE", "models"))
    cache_root.mkdir(parents=True, exist_ok=True)
    download_directory = cache_root / Path(local_path).name

    logger.info(f"Starting HF download of {repository}")

    snapshot_download(
        repo_id=repository,
        local_dir=str(download_directory),
        token=read_hugging_face_token(),
    )

    downloaded = existing_model_directory(download_directory)
    if downloaded is None:
        raise FileNotFoundError(
            f"Downloaded repository {repository!r} does not contain a usable model."
        )

    logger.info(f"completed HF download of {repository}")

    return downloaded
