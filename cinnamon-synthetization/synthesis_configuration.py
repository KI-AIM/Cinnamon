"""Load, validate and prepare synthesis configurations."""

import os
from copy import deepcopy
from functools import lru_cache

import yaml

from synthetic_tabular_data_generator.llm import get_llm_profile_names
from synthetic_tabular_data_generator.embedding_profiles import get_embedding_profile_names


SYNTHESIZER_CONFIG_DIR = os.path.join(
    os.path.dirname(__file__),
    "synthetic_tabular_data_generator",
    "synthesizer_config",
)
HYPERPARAMETER_TUNING_DIR = os.path.join(os.path.dirname(__file__), "hyperparameter_tuning")

PROCESSING_MODALITY_STRUCTURED_ONLY = "structured_only"
PROCESSING_MODALITY_TEXT_ONLY = "text_only"
PROCESSING_MODALITY_MIXED = "mixed"
PROCESSING_MODALITIES = {
    PROCESSING_MODALITY_STRUCTURED_ONLY,
    PROCESSING_MODALITY_TEXT_ONLY,
    PROCESSING_MODALITY_MIXED,
}


def validate_attribute_config(attribute_config):
    configurations = attribute_config.get("configurations")
    if not isinstance(configurations, list) or not configurations:
        raise ValueError("Uploaded file 'attribute_config' must define a non-empty 'configurations' list.")


def validate_algorithm_config(algorithm_config):
    synthetization_configuration = algorithm_config.get("synthetization_configuration")
    if not isinstance(synthetization_configuration, dict):
        raise ValueError("Uploaded file 'algorithm_config' must define 'synthetization_configuration'.")

    algorithm_section = synthetization_configuration.get("algorithm")
    if not isinstance(algorithm_section, dict):
        raise ValueError(
            "Uploaded file 'algorithm_config' must define 'synthetization_configuration.algorithm'."
        )


@lru_cache(maxsize=None)
def load_synthesizer_config(synthesizer_name):
    config_file = os.path.join(SYNTHESIZER_CONFIG_DIR, f"{synthesizer_name}.yaml")
    with open(config_file, "r", encoding="utf-8") as handle:
        return yaml.safe_load(handle) or {}


@lru_cache(maxsize=1)
def load_study_config():
    config_file = os.path.join(HYPERPARAMETER_TUNING_DIR, "study.yaml")
    with open(config_file, "r", encoding="utf-8") as handle:
        return yaml.safe_load(handle) or {}


def get_study_parameter_default(parameter_name):
    study_config = load_study_config()
    parameters = study_config.get("configurations", {}).get("study", {}).get("parameters", [])
    for parameter in parameters:
        if parameter.get("name") == parameter_name and "default_value" in parameter:
            return parameter["default_value"]
    raise RuntimeError(
        f"Missing default_value for hyperparameter tuning parameter '{parameter_name}' in study.yaml."
    )


def get_data_modality(synthesizer_name):
    config = load_synthesizer_config(synthesizer_name)
    capabilities = config.get("processing_capabilities", {})
    data_modality = capabilities.get("data_modality")
    if data_modality not in PROCESSING_MODALITIES:
        raise ValueError(
            f"Invalid data_modality for synthesizer '{synthesizer_name}': {data_modality!r}."
        )
    return data_modality


def split_attribute_configurations(attribute_config):
    all_configurations = attribute_config.get("configurations", [])
    structured = []
    text = []
    for column_config in all_configurations:
        if str(column_config.get("type", "")).upper() == "TEXT":
            text.append(column_config)
        else:
            structured.append(column_config)
    return structured, text


def inject_llm_profile_parameter(config_content):
    configurations = config_content.setdefault("configurations", {})
    profile_names = get_llm_profile_names()
    if not profile_names:
        return config_content

    llm_profile_group = configurations.setdefault(
        "llm_profile",
        {
            "display_name": "LLM Profile",
            "description": "Select which configured LLM instance should be used.",
            "parameters": [],
        },
    )
    parameters = llm_profile_group.get("parameters", [])
    if not isinstance(parameters, list):
        return config_content

    llm_profile_parameter = None
    for parameter in parameters:
        if parameter.get("name") == "llm_profile":
            llm_profile_parameter = parameter
            break

    if llm_profile_parameter is None:
        llm_profile_parameter = {
            "name": "llm_profile",
            "type": "string",
            "label": "LLM Profile",
            "description": "Select the configured LLM profile (model endpoint and credentials).",
            "default_value": "",
            "values": profile_names,
            "mandatory": True,
        }
        parameters.append(llm_profile_parameter)
    else:
        llm_profile_parameter["values"] = profile_names
        llm_profile_parameter["default_value"] = ""
        llm_profile_parameter["mandatory"] = True

    return config_content


def inject_embedding_profile_parameter(config_content):
    profile_names = get_embedding_profile_names()
    if not profile_names:
        return config_content

    model_parameter_group = (config_content.setdefault("configurations", {})).get("model_parameter")
    if not isinstance(model_parameter_group, dict):
        return config_content

    parameters = model_parameter_group.get("parameters", [])
    if not isinstance(parameters, list):
        return config_content

    embedding_model_parameter = None
    for parameter in parameters:
        if parameter.get("name") == "embedding_model":
            embedding_model_parameter = parameter
            break

    if embedding_model_parameter is None:
        return config_content

    embedding_model_parameter["values"] = profile_names
    embedding_model_parameter["default_value"] = ""
    embedding_model_parameter["mandatory"] = False
    return config_content


@lru_cache(maxsize=None)
def load_text_synthesis_defaults(text_synthesizer_name):
    synthesizer_config = load_synthesizer_config(text_synthesizer_name)
    defaults = {
        "llm_profile": {},
        "model_parameter": {},
        "model_fitting": {},
        "sampling": {},
    }
    sections = synthesizer_config.get("configurations", {})
    for section_name in defaults:
        parameters = sections.get(section_name, {}).get("parameters", [])
        for parameter in parameters:
            parameter_name = parameter.get("name")
            if not parameter_name or "default_value" not in parameter:
                continue
            defaults[section_name][parameter_name] = parameter.get("default_value")

    return defaults


def build_text_synthesis_algorithm_config(algorithm_config, synthesizer_name, num_samples):
    config = deepcopy(algorithm_config) if algorithm_config else {}

    algorithm_section = config.setdefault("synthetization_configuration", {}).setdefault("algorithm", {})
    algorithm_section.setdefault("synthesizer", synthesizer_name)
    algorithm_section.setdefault("llm_profile", {})
    algorithm_section.setdefault("model_parameter", {})
    algorithm_section.setdefault("model_fitting", {})
    algorithm_section.setdefault("sampling", {})

    defaults = load_text_synthesis_defaults(synthesizer_name)

    llm_profile = algorithm_section["llm_profile"]
    for key, value in defaults.get("llm_profile", {}).items():
        llm_profile.setdefault(key, value)

    model_parameter = algorithm_section["model_parameter"]
    for key, value in defaults.get("model_parameter", {}).items():
        model_parameter.setdefault(key, value)

    model_fitting = algorithm_section["model_fitting"]
    for key, value in defaults.get("model_fitting", {}).items():
        model_fitting.setdefault(key, value)

    sampling = algorithm_section["sampling"]
    for key, value in defaults.get("sampling", {}).items():
        sampling.setdefault(key, value)
    if sampling.get("num_samples") is None:
        sampling["num_samples"] = num_samples

    return config
