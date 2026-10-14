## Text anonymization service

### Local model setup (without Docker)

After cloning the repository, in the root of this module, `cinnamon-text-anonymization`, run:

```bash
python3 -m venv .venv
source .venv/bin/activate
python -m pip install -e .
```
Then load the env variables
```bash
  set -a 
  source .env 
  set +a
```
Then start the service with `python app.py`.

__NOTE__ _The service expects either having the local models stored under `cinnamon-text-anonymization/models` or to provide a Hugging Face (HF) token to fetch the required models_.
For more details we refer to __Environment variables__.

The commands below assume you're in the project root folder.

### Prerequisites

1. Either download the models under `cinnamon-text-anonymization/models`, or provide a hf_token to download them from Hugging Face.
2. To be able to download the models from Hugging Face, create the directory `secrets` in the root of the Cinnamon project, and within the directory create a file called `hf_token` and past your HF token there.

These will be set as environmental variables.


### Environment variables
In the root of the project, in `.env` the following environmental variables should be set.
(_for example paths and usages check `.env.example` in the root of the project_):

#### When using local models
`TEXT_ANONYMIZATION_MODEL_HOST_PATH` - the local path where the models are stored
`TEXT_ANONYMIZATION_MODEL_MOUNT_MODE`=`ro` - relevant when using Docker. We want to grant the Docker container read-only access to the models if we have them downloaded locally.

#### When loading models through Hugging Face (HF)
`HF_TOKEN_FILE` - the local path to a file storing the HF token
`TEXT_ANONYMIZATION_MODEL_HOST_PATH` - the local path where you want the models downloaded from HF to be stored
`TEXT_ANONYMIZATION_MODEL_MOUNT_MODE`=`rw` - relevant when using Docker. We need to allow the Docker container to write the downloaded HF model to TEXT_ANONYMIZATION_MODEL_HOST_PATH

`TEXT_ANONYMIZATION_STATUS_HOST_PATH` remains read-write in both modes because job status and cancellation state are stored there.

Raw assistant responses from the LLM are appended as plain text to a new,
timestamped file for each application process, for example
`cinnamon-text-anonymization/outputs/llm_responses_20261014_153045_123456.log`.
When running in Docker, `TEXT_ANONYMIZATION_STATUS_DIR` points this file to the
mounted status directory. These responses can contain personal data, so
protect or remove them when they are no longer needed. 

Long texts are sent to the LLM in overlapping character windows. The defaults
are suitable for the current remote profiles and avoid exceeding their context
or output limits:

`TEXT_ANONYMIZATION_LLM_CHUNK_SIZE=2000` (characters)

`TEXT_ANONYMIZATION_LLM_CHUNK_OVERLAP=200` (characters)

If the model can support more tokens, set `TEXT_ANONYMIZATION_LLM_CHUNK_SIZE` to a sufficiently large character value if the selected model can process the complete text in one request.
and `TEXT_ANONYMIZATION_LLM_CHUNK_OVERLAP` = 0.

Each LLM profile uses `CINNAMON_LLM_PROFILE_<PROFILE_ID>_MAX_TOKENS` for the
completion limit; the Python service default is `2048` when the profile does
not define it. The active prompt also explicitly requests JSON output only without
reasoning or `<think>` blocks.

The local token-classification models currently use the model keys XLM and GELECTRA, thus the following environmental variables are provided:
`TEXT_ANONYMIZATION_[model_name]_HF_REPOSITORY`. These should be specified with:

`TEXT_ANONYMIZATION_XLM_HF_REPOSITORY` - the HF repository storing the XLM model
`TEXT_ANONYMIZATION_GELECTRA_HF_REPOSITORY` - the HF repository storing the GELECTRA model

PII and sensitive-data anonymization use separate model directories and
repositories. Sensitive-data models use the `SENSITIVE_` environment-variable
prefix, for example:

`TEXT_ANONYMIZATION_SENSITIVE_XLM_LOCAL_PATH`

`TEXT_ANONYMIZATION_SENSITIVE_GELECTRA_LOCAL_PATH`

`TEXT_ANONYMIZATION_SENSITIVE_XLM_HF_REPOSITORY`

`TEXT_ANONYMIZATION_SENSITIVE_GELECTRA_HF_REPOSITORY`

The frontend sends the two optional configurations as
`textAnonymizationConfigurations.pii` and
`textAnonymizationConfigurations.sensitive`. Each configuration has its own
`enabled` flag, model type, selected columns, confidence threshold, and
anonymization mode. The sensitive LLM prompt currently emits the labels
`MEDICATION`, `TREATMENT`, and `DIAGNOSIS`.

For the `LLM` model type, set `llmProfile` in the text-anonymization configuration to
the profile name (or ID) configured through the shared `CINNAMON_LLM_PROFILE_*`
environment variables, for example `your_configured_profile_id`. LLM entities do not currently provide a calibrated confidence score,
so valid structured LLM entities are treated as confidence `1.0`.

Example configuration:

```json
{
  "anonymization": {
    "textAnonymizationConfigurations": {
      "pii": {
        "enabled": true,
        "modelType": "LLM",
        "llmProfile": "BIH GPU",
        "columns": ["dokument_text"],
        "confidenceThreshold": 0.9,
        "anonymizationMode": "redact"
      },
      "sensitive": {
        "enabled": false,
        "modelType": "GELECTRA",
        "columns": ["dokument_text"],
        "confidenceThreshold": 0.9,
        "anonymizationMode": "redact"
      }
    }
  }
}
```

### Using Docker

To build only the cinnamon-text-anonymization image, run:
```bash
docker compose -f docker-compose.yml -f docker-compose-build.yml build cinnamon-text-anonymization
```

To build all project images, run:
```bash
docker compose -f docker-compose.yml -f docker-compose-build.yml build
```

To run the built cinnamon-text-anonymization, forcing container recreation for easier testing, run:
```bash
docker compose -f docker-compose.yml -f docker-compose-build.yml up -d --force-recreate cinnamon-text-anonymization
```

To run all cinnamon images, run:
```bash
docker compose up -d
```

To follow along the cinnamon-text-anonymization container logs:
```bash
docker compose logs -f cinnamon-text-anonymization
```

Or, to follow along all docker compose container logs:
```bash
docker compose logs -f
```
