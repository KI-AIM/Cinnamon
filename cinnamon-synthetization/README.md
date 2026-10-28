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

## Structured data and free text

Datasets with structured columns (including columns added by text extraction) first use a
structured synthesizer: CTGAN, TVAE, ARF, DDPM, RTVAE, Bayesian Network, or the structured
LLM synthesizer. Their existing model, fitting, sampling, and supported Optuna tuning
settings apply unchanged. Optuna tuning is supported for the six classical synthesizers;
its sampler, pruner, trial count, and timeout are configurable in the frontend.

For datasets that also contain TEXT columns, select a second method: mixed-data paraphrase,
expert-guided synthesis, indirect-identifier rewrite, or retrieval-guided synthesis. This stage generates
one text per synthetic row and per TEXT column, preserving structured values, missingness,
row order, and row count exactly. For retrieval-guided synthesis, its Number of Samples
sets the total output count and overrides the structured sampling count before generation.
This may exceed the source dataset size. Other text methods use the structured sampling count.
Text-only datasets continue to use their existing text-only methods.

Synthetic structured values are ground truth. Prompts require every non-missing fact in the
text as an exact `column: value` entry, embedded in sentences or a compact factual section
alongside the narrative. The response validator checks these entries and retries incomplete
responses; exhausted retries fail the job rather than export incomplete text. This checks
literal coverage, not semantic consistency of the entire narrative. Prompts separately
require the narrative to agree with all ground-truth facts. Reference texts may contribute
additional information when consistent with those facts. Identifier-rewrite rules apply to
additional reference details, never to the synthetic structured values.

Retrieval queries use the synthetic structured facts. The existing embedding settings
and similarity weights remain available; set text similarity weight to `0` and structured
similarity weight to `1` for attribute-only retrieval.

The frontend saves both stages in one configuration. Existing direct mixed-data configurations
must be reconfigured by selecting a structured synthesizer first. API example:

```yaml
synthetization_configuration:
  algorithm:
    id: ctgan
    synthesizer: ctgan
    model_parameter:
      number_of_layers: 2
      number_of_units_in_layers: 128
    model_fitting:
      epochs: 100
      batch_size: 100
    sampling:
      num_samples: 100
    hyperparameter_tuning:
      enabled: false
      sampler: tpe
      pruner: median
      n_trials: 10
      timeout_minutes: 30
  text_synthesis_configuration:
    synthetization_configuration:
      algorithm:
        synthesizer: llm_mixed_data_embedding_nearest_neighbor_synthesis
        llm_profile:
          llm_profile: Your configured profile name
        model_parameter:
          few_shot_examples: 3
          text_similarity_weight: 0
          structured_similarity_weight: 1
        sampling:
          temperature: 0.2
          top_p: 0.9
```

For two-stage jobs, the serialized model contains `structured_synthesis` (the structured
model bytes) and `text_synthesis` (a mapping from TEXT column names to model bytes).

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

Run exactly one Gunicorn web worker: the job registry is local to that process.
Synthesis and callback retries run in separate child processes. Configure
`CINNAMON_MAX_SYNTHESIS_JOBS` to limit concurrent children (default: `1`, minimum:
`1`). Additional starts receive HTTP 429; an already running session receives
HTTP 409 without changing its status. The local development server disables the
reloader for the same reason. Multiple web workers or replicas require a shared
job manager and are not supported by this registry.

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

The additional `job_state` field is `running`, `delivering`, `succeeded`,
`failed`, `cancelled`, or `delivery_failed`; `error` describes a failure.
`delivery_failed` means synthesis completed but its results were not confirmed
by the callback receiver. Existing step/component status fields remain available.
Unexpected child-process exits are reconciled when the API next inspects tasks,
including on every status request.

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

### Retry a failed result delivery

```text
POST /retry_synthetization_callback
```

Supply `session_key` as form-data. A successful start returns HTTP 202 with the
retry process PID; the existing status and cancellation endpoints apply to it.
This sends the saved CSV and model to the original callback URL without running
the synthesizer again. Running sessions return 409, missing results 404, and a
full job limit 429.

Results are saved atomically under `outputs/pending_callbacks/` before sending
and removed after a successful delivery. Failed deliveries remain available for
an explicit retry and prevent a new synthesis from overwriting that session.
There is no automatic HTTP retry: the platform callback is not idempotent, so
after an ambiguous timeout check whether the receiver already accepted the result
before retrying. A cancellation cannot undo a callback already accepted remotely.
Delivery failures do not send a contradictory synthesis-error callback.

Pending files can contain sensitive model information. Remove abandoned results
when no longer needed; they are not expired automatically. Preserve both
`outputs/status/` and `outputs/pending_callbacks/` on persistent storage if results
must survive container replacement. Running jobs are not resumed after a web
worker/service restart; this is not a persistent job queue.

## Callback Output

When a synthesis job finishes successfully, the callback receives:

- `synthetic_data`
  generated CSV
- `model`
  serialized synthesizer model

In case of an error, the callback receives an error payload instead.

### Model export contract

Model files remain pickled synthesizer objects; two-stage exports retain the
`structured_synthesis` / `text_synthesis` mapping described above. Exports are
built from a fresh instance, not from the complete live object's state. Input
dataframes, raw algorithm configuration, progress callbacks and LLM clients
(including credentials and prompt loggers) are not exported. Exporting does
not modify the running synthesizer.

- CTGAN, TVAE, RTVAE, DDPM and Bayesian Network exports retain the fitted backend,
  attribute schema and sampling count. After `loaded = synth.load_model(path)`,
  `loaded.sample()` remains available. Supply a dataset before fitting again.
- ARF exports retain its attribute schema, model parameters and sampling count,
  but omit the fitted backend: arfpy retains original rows and reuses them in
  generation. Supply a dataset, call `initialize_synthesizer()` and `fit()`
  before sampling the loaded object.
- LLM exports retain only the synthesizer type and attribute schema. Profiles,
  reference texts, retrieval indexes, expert guidance and cached prompts are
  omitted because they can contain source information. Reinitialize algorithm
  configuration, input/reference datasets and the LLM backend, then call `fit()`
  before reuse. Credentials are resolved again from the runtime environment.

Fitted statistical models and attribute schemas are still sensitive artifacts:
this export contract is not an anonymization or differential-privacy guarantee.
Existing exported files are not retroactively cleaned. Load pickle files only
from trusted sources.

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

Prompt logging is disabled by default. Enable it explicitly through environment
variables when debugging prompt construction and LLM responses. Disabled logging
creates no prompt files or log directories, even when a session key is set.

Available variables:

- `CINNAMON_LLM_ENABLE_PROMPT_LOGGING=true/false` (default: `false`)
- `CINNAMON_LLM_LOG_DIR=/custom/path`

When enabled, logs are written under `outputs/prompts/<session_key>/` by default,
or `<CINNAMON_LLM_LOG_DIR>/<session_key>/` when an override is configured.
Each successful LLM response is logged once with its prompt and decoding metadata.
Logs can contain original texts and reference information. Disabling logging does
not delete previously written files.

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
