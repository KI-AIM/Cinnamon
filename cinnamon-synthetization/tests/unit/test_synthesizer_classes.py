import ast
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[2]
SYNTHESIZER_CLASSES_FILE = PROJECT_ROOT / "synthesizer_classes.py"
ALGORITHM_DIR = PROJECT_ROOT / "synthetic_tabular_data_generator" / "algorithms"


def _class_reference_to_name(node: ast.AST) -> str:
    if isinstance(node, ast.Name):
        return node.id
    if isinstance(node, ast.Attribute):
        return node.attr
    raise AssertionError("class reference must be a name")


def _load_synthesizer_classes() -> dict:
    tree = ast.parse(SYNTHESIZER_CLASSES_FILE.read_text(encoding="utf-8"))

    for node in tree.body:
        if not isinstance(node, ast.Assign):
            continue

        for target in node.targets:
            if isinstance(target, ast.Name) and target.id == "synthesizer_classes":
                assert isinstance(node.value, ast.Dict)
                data = {}

                for key_node, value_node in zip(node.value.keys, node.value.values):
                    name = ast.literal_eval(key_node)
                    assert isinstance(value_node, ast.Dict)

                    config = {}
                    for cfg_key_node, cfg_value_node in zip(value_node.keys, value_node.values):
                        cfg_key = ast.literal_eval(cfg_key_node)
                        if cfg_key == "class":
                            config[cfg_key] = _class_reference_to_name(cfg_value_node)
                        else:
                            config[cfg_key] = ast.literal_eval(cfg_value_node)

                    data[name] = config

                return data

    raise AssertionError("synthesizer_classes definition not found")


def _algorithm_class_names() -> set[str]:
    class_names = set()
    for path in ALGORITHM_DIR.glob("*.py"):
        tree = ast.parse(path.read_text(encoding="utf-8"))
        for node in tree.body:
            if isinstance(node, ast.ClassDef):
                class_names.add(node.name)
    return class_names


synthesizer_classes = _load_synthesizer_classes()
ALGORITHM_CLASS_NAMES = _algorithm_class_names()


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
        assert isinstance(config["class"], str), f"{name}.class must be a class name"
        assert config["class"] in ALGORITHM_CLASS_NAMES, (
            f"{name}.class must reference an algorithm class"
        )
        for field in STRING_FIELDS:
            assert isinstance(config[field], str), f"{name}.{field} must be a string"
        assert config["URL"].endswith(".yaml"), f"{name}.URL must end with .yaml"
