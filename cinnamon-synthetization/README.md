# KI-AIM Synthetization Module

The synthetization module generates synthetic tabular datasets for the KI-AIM platform. It supports both classical tabular synthesizers such as CTGAN, TVAE, ARF, DDPM, Bayesian Networks, and RTVAE, as well as LLM-based synthesizers for fully tabular generation and text enrichment.

The module is designed to run as a backend service inside the Cinnamon platform, but it can also be started and tested on its own.

## What This Module Does

The service exposes APIs to:

- list available synthesizers
- return frontend configuration YAML files for each synthesizer
- start a synthesis job
- report synthesis status during execution
- cancel a running synthesis job

For LLM-based workflows, the module supports:

- fully synthetic tabular generation with an LLM
- text-only enrichment of already synthesized structured rows
- selectable LLM profiles loaded from environment variables

## Project Structure

- `app.py`
  Flask API entrypoint
- `synthetic_tabular_data_generator/algorithms/`
  synthesizer implementations
- `synthetic_tabular_data_generator/synthesizer_config/`
  frontend-facing YAML configuration files
- `synthetic_tabular_data_generator/llm/`
  LLM client, prompt building, validation, and support code
- `outputs/status/`
  per-session status files
- `outputs/prompts/`
  optional prompt logs for LLM runs

## Run With Docker

From the repository root:

```bash
docker build -t synthetization_module -f cinnamon-synthetization/Dockerfile .
docker run -p 5000:5000 synthetization_module
```

The service will then be available at:

```text
http://127.0.0.1:5000
```

## Run Locally

From `cinnamon-synthetization/`:

```bash
pip install -r requirements.txt
python app.py
```

Depending on your setup you may prefer `python3` instead of `python`.

## API Overview

### List available synthesizers

```text
GET /get_algorithms
```

Returns the registered synthesizers and their metadata.

### Get synthesizer configuration

Example:

```text
GET /synthetic_tabular_data_generator/synthesizer_config/ctgan.yaml
```

### Get synthesis status

```text
GET /get_status/<session_key>
```

Returns the current YAML-backed status as JSON.

For LLM-enabled pipelines the returned status may include:

- global pipeline steps in `status`
- stage-specific details in `components.structured_synthesis`
- stage-specific details in `components.llm_synthesis`

### Start a synthesis process

Example:

```text
POST /start_synthetization_process/ctgan
```

Required multipart form-data fields:

- `data`
  source dataset as CSV
- `attribute_config`
  attribute configuration as YAML
- `algorithm_config`
  synthesizer configuration as YAML
- `session_key`
  unique session identifier
- `callback`
  callback URL

Optional multipart field:

- `original_data`
  original reference dataset, used by text synthesis workflows

### Cancel a running synthesis process

```text
POST /cancel_synthetization_process
```

Required form-data fields:

- `session_key`
- `pid`

## Callback Output

When a synthesis job finishes successfully, the callback receives:

- `synthetic_data`
  generated CSV
- `model`
  serialized synthesizer model

In case of an error, the callback receives an error payload instead.

## LLM Configuration Via `.env`

LLM-based synthesizers require an explicit `llm_profile` selection. The frontend exposes the configured profiles in the synthesizer configuration when they are defined.

### Selectable LLM profiles

Profiles are defined in `.env` and shown in the UI as selectable options.

First define the profile IDs:

```env
CINNAMON_LLM_PROFILE_IDS=qwen14b,ollama-qwen3-8b
```

Then define one block per profile. The profile ID is normalized to uppercase with non-alphanumeric characters replaced by `_`.

Example:

```env
CINNAMON_LLM_PROFILE_QWEN14B_NAME="Qwen 14B (GPU 7086)"
CINNAMON_LLM_PROFILE_QWEN14B_PROVIDER="openai_compatible"
CINNAMON_LLM_PROFILE_QWEN14B_MODEL_NAME="Qwen/Qwen2.5-14B-Instruct"
CINNAMON_LLM_PROFILE_QWEN14B_BASE_URL="http://gpu.example.org:7086"
CINNAMON_LLM_PROFILE_QWEN14B_ENDPOINT_PATH="/v1/chat/completions"
CINNAMON_LLM_PROFILE_QWEN14B_HEALTHCHECK_PATH="/v1/models"
CINNAMON_LLM_PROFILE_QWEN14B_API_KEY=""
CINNAMON_LLM_PROFILE_QWEN14B_TIMEOUT_SECONDS=120
CINNAMON_LLM_PROFILE_QWEN14B_MAX_RETRIES=3
CINNAMON_LLM_PROFILE_QWEN14B_VERIFY_SSL=true
CINNAMON_LLM_PROFILE_QWEN14B_MAX_TOKENS=4096

CINNAMON_LLM_PROFILE_OLLAMA_QWEN3_8B_NAME="Ollama Qwen3 8B (local)"
CINNAMON_LLM_PROFILE_OLLAMA_QWEN3_8B_PROVIDER="ollama"
CINNAMON_LLM_PROFILE_OLLAMA_QWEN3_8B_MODEL_NAME="qwen3:8b"
CINNAMON_LLM_PROFILE_OLLAMA_QWEN3_8B_BASE_URL="http://host.docker.internal:11434"
CINNAMON_LLM_PROFILE_OLLAMA_QWEN3_8B_ENDPOINT_PATH="/api/generate"
CINNAMON_LLM_PROFILE_OLLAMA_QWEN3_8B_HEALTHCHECK_PATH="/api/tags"
CINNAMON_LLM_PROFILE_OLLAMA_QWEN3_8B_API_KEY=""
CINNAMON_LLM_PROFILE_OLLAMA_QWEN3_8B_TIMEOUT_SECONDS=300
CINNAMON_LLM_PROFILE_OLLAMA_QWEN3_8B_MAX_RETRIES=3
CINNAMON_LLM_PROFILE_OLLAMA_QWEN3_8B_VERIFY_SSL=true
CINNAMON_LLM_PROFILE_OLLAMA_QWEN3_8B_MAX_TOKENS=8192
```

### Which values are required?

For a profile to be available, it must at least define:

- `PROVIDER`
- `MODEL_NAME`
- `BASE_URL`

The remaining fields are strongly recommended.

### Where do decoding settings come from?

The runtime configuration is resolved in this order:

1. selected LLM profile
2. synthesizer configuration values

For decoding, these values are especially important:

- `temperature`
- `top_p`
- `max_tokens`

## Important Disclaimer About `max_tokens`

Per-profile `*_MAX_TOKENS` must be chosen carefully.

This value depends on:

- the selected model
- the model context window
- the prompt size
- the number of few-shot rows
- the number and length of text columns
- the average length of free-text content in the dataset

If `max_tokens` is too low:

- responses may be truncated
- JSON output may become invalid
- long text fields may be incomplete

If `max_tokens` is too high:

- the request may exceed the model context window
- generation may become slow or unstable
- some providers may reject the request

Practical recommendation:

- start conservatively
- test with realistic long-text rows from your dataset
- increase `max_tokens` only as needed
- use larger values for text-heavy medical notes than for short categorical tabular generation

For text synthesis, `max_tokens` should always be evaluated together with dataset text length and prompt size.

## LLM Prompt Logging

Prompt logging can be enabled through environment variables. This is useful for debugging prompt construction and LLM responses.

Available variables:

- `CINNAMON_LLM_ENABLE_PROMPT_LOGGING=true/false`
- `CINNAMON_LLM_LOG_DIR=/custom/path`

Logs are written under `outputs/prompts/` by default.

## Notes On LLM-Based Synthesizers

### `llm_nearest_neighbor_few_shot_text_synthesis`

Used when structured rows already exist and only TEXT fields should be synthesized or enriched. It can optionally repair inconsistent structured values before generating text.

## Development Notes

- Status files are written to `outputs/status/`
- LLM prompt logs can be written to `outputs/prompts/`
- unit and integration tests are located in `tests/`

## Troubleshooting

### `Unknown llm_profile`

The selected profile name is not available from `.env`. Check:

- `CINNAMON_LLM_PROFILE_IDS`
- the profile block names
- the selected UI value

### LLM profile does not appear in the UI

Check that the profile defines at least:

- `PROVIDER`
- `MODEL_NAME`
- `BASE_URL`

If one of these is missing, the profile is skipped.

### LLM requests fail with invalid JSON or truncated output

Usually this points to one of:

- `max_tokens` too low
- prompt too large
- model unsuitable for long structured JSON output

### Local Ollama container cannot be reached from Docker

If you run the module in Docker and Ollama on the host, `host.docker.internal` is often the correct base URL host.

## License / Context

This module is part of the KI-AIM Cinnamon project for privacy-preserving synthetic data generation in the medical domain.
