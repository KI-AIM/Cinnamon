from synthesizer_classes import synthesizer_classes


def test_synthesizer_classes_is_dict():
    assert isinstance(synthesizer_classes, dict)
    assert synthesizer_classes


def test_each_synthesizer_has_exact_keys():
    REQUIRED_KEYS = {
    "version",
    "type",
    "class",
    "display_name",
    "description",
    "URL",
    }

    for name, config in synthesizer_classes.items():
        assert isinstance(config, dict)
        assert set(config.keys()) == REQUIRED_KEYS, f"{name} must use exact keys"


def test_field_types_and_url_suffix():
    STRING_FIELDS = ("version", "type", "display_name", "description", "URL")

    for name, config in synthesizer_classes.items():
        assert isinstance(config["class"], type), f"{name}.class must be a class"
        for field in STRING_FIELDS:
            assert isinstance(config[field], str), f"{name}.{field} must be a string"
        assert config["URL"].endswith(".yaml"), f"{name}.URL must end with .yaml"
