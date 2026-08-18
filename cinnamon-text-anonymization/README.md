## Text anonymization service

### Local model setup (without Docker)

After cloning the repository, in the root of this module, `cinnamon-text-anonymization`, run:

```bash
python3 -m venv .venv
source .venv/bin/activate
python -m pip install -e .
```
The start the service with `python app.py`.

__NOTE__ _The service expects either having the local models stored under `cinnamon-text-anonymization/models` or to provide a Hugging Face (HF) token to fetch the required models_.
For more details we refer to __Environment variables__.


### Using Docker

The commands below assume you're in the project root folder.

#### Prerequisites

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

The current code assumes that there are models called XLM and GELECTRA, thus the following environmental variables are provided:
`TEXT_ANONYMIZATION_[model_name]_HF_REPOSITORY`. These should be specified with:

`TEXT_ANONYMIZATION_XLM_HF_REPOSITORY` - the HF repository storing the XLM model
`TEXT_ANONYMIZATION_GELECTRA_HF_REPOSITORY` - the HF repository storing the GELECTRA model

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
