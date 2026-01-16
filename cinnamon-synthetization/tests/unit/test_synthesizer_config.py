from pathlib import Path

import yaml

from synthesizer_classes import synthesizer_classes


CONFIG_DIR = (
    Path(__file__).resolve().parents[2]
    / "synthetic_tabular_data_generator"
    / "synthesizer_config"
)

TOP_LEVEL_KEYS = {
    "name",
    "version",
    "type",
    "display_name",
    "description",
    "URL",
    "configurations",
}
CONFIG_SECTIONS = {"model_parameter", "model_fitting", "sampling"}
SECTION_KEYS = {"display_name", "description", "parameters"}

PARAM_REQUIRED_KEYS = {"name", "type", "label", "description", "default_value"}
PARAM_OPTIONAL_KEYS = {"min_value", "max_value", "values"}
PARAM_ALLOWED_KEYS = PARAM_REQUIRED_KEYS | PARAM_OPTIONAL_KEYS
ALLOWED_PARAM_TYPES = {"integer", "float", "string", "list"}


def _load_yaml(path: Path) -> dict:
    with path.open("r", encoding="utf-8") as handle:
        return yaml.safe_load(handle)


def _is_number(value) -> bool:
    return isinstance(value, (int, float)) and not isinstance(value, bool)


def test_config_files_exist():
    files = sorted(CONFIG_DIR.glob("*.yaml"))
    assert files, "No synthesizer config files found"


def test_top_level_structure_and_filename_match():
    for path in CONFIG_DIR.glob("*.yaml"):
        config = _load_yaml(path)
        assert set(config.keys()) == TOP_LEVEL_KEYS
        assert config["name"] == path.stem
        assert isinstance(config["version"], str) and config["version"]
        assert isinstance(config["type"], str) and config["type"]
        assert isinstance(config["display_name"], str) and config["display_name"]
        assert isinstance(config["description"], str) and config["description"]
        assert isinstance(config["URL"], str) and config["URL"]
        assert config["URL"].startswith("/start_synthetization_process/")
        assert config["URL"].endswith(f"/{config['name']}")
        assert set(config["configurations"].keys()) == CONFIG_SECTIONS


def test_configuration_sections_and_parameters():
    for path in CONFIG_DIR.glob("*.yaml"):
        config = _load_yaml(path)

        for section_name, section in config["configurations"].items():
            assert section_name in CONFIG_SECTIONS
            assert set(section.keys()) == SECTION_KEYS
            assert isinstance(section["display_name"], str) and section["display_name"]
            assert isinstance(section["description"], str) and section["description"]
            assert isinstance(section["parameters"], list) and section["parameters"]

            seen_names = set()
            for param in section["parameters"]:
                assert PARAM_REQUIRED_KEYS.issubset(param.keys())
                assert set(param.keys()) <= PARAM_ALLOWED_KEYS

                name = param["name"]
                assert isinstance(name, str) and name
                assert name not in seen_names
                seen_names.add(name)

                param_type = param["type"]
                assert isinstance(param_type, str) and param_type in ALLOWED_PARAM_TYPES
                assert isinstance(param["label"], str) and param["label"]
                assert isinstance(param["description"], str) and param["description"]

                default_value = param["default_value"]
                if param_type == "integer":
                    assert isinstance(default_value, int) and not isinstance(default_value, bool)
                elif param_type == "float":
                    assert _is_number(default_value)
                elif param_type == "string":
                    assert isinstance(default_value, str)
                elif param_type == "list":
                    assert isinstance(default_value, list)

                if "values" in param:
                    assert isinstance(param["values"], list) and param["values"]
                    assert default_value in param["values"]

                if _is_number(default_value):
                    assert "min_value" in param and "max_value" in param
                if "min_value" in param or "max_value" in param:
                    assert param_type in {"integer", "float"}
                    if "min_value" in param:
                        assert _is_number(param["min_value"])
                    if "max_value" in param:
                        assert _is_number(param["max_value"])
                    if "min_value" in param and "max_value" in param:
                        assert param["min_value"] <= param["max_value"]
                        assert param["min_value"] <= default_value <= param["max_value"]


def test_yaml_matches_synthesizer_classes_name_version_type():
    yaml_configs = {}
    for path in CONFIG_DIR.glob("*.yaml"):
        config = _load_yaml(path)
        yaml_configs[config["name"]] = config

    assert set(yaml_configs.keys()) == set(synthesizer_classes.keys())

    for name, config in yaml_configs.items():
        synthesizer_config = synthesizer_classes[name]
        assert config["name"] == name
        assert config["version"] == synthesizer_config["version"]
        assert config["type"] == synthesizer_config["type"]
