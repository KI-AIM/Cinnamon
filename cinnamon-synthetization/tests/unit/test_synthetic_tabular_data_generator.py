import ast
import sys
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

ALGORITHM_DIR = PROJECT_ROOT / "synthetic_tabular_data_generator" / "algorithms"
SKIP_FILES = {"__init__.py"}


def _algorithm_files() -> list[Path]:
    return sorted(path for path in ALGORITHM_DIR.glob("*.py") if path.name not in SKIP_FILES)


def _annotation_to_str(node: ast.AST | None) -> str:
    if node is None:
        return ""
    if isinstance(node, ast.Name):
        return node.id
    if isinstance(node, ast.Attribute):
        base = _annotation_to_str(node.value)
        return f"{base}.{node.attr}" if base else node.attr
    if isinstance(node, ast.Subscript):
        base = _annotation_to_str(node.value)
        inner = _annotation_to_str(node.slice)
        return f"{base}[{inner}]" if inner else base
    if isinstance(node, ast.Tuple):
        return ", ".join(_annotation_to_str(elt) for elt in node.elts)
    if isinstance(node, ast.Constant):
        if node.value is None:
            return "None"
        if isinstance(node.value, str):
            return node.value
        return str(node.value)
    return ""


def _annotation_matches(annotation: str, expected_parts: list[str]) -> bool:
    return any(part in annotation for part in expected_parts)


def _is_tabular_base(base: ast.expr) -> bool:
    if isinstance(base, ast.Name):
        return base.id == "TabularDataSynthesizer"
    if isinstance(base, ast.Attribute):
        return base.attr == "TabularDataSynthesizer"
    return False


def _get_params(method: ast.FunctionDef) -> list[ast.arg]:
    return method.args.posonlyargs + method.args.args


def _synth_classes():
    entries = []
    for path in _algorithm_files():
        tree = ast.parse(path.read_text(encoding="utf-8"))
        classes = [node for node in tree.body if isinstance(node, ast.ClassDef)]
        for cls in classes:
            if any(_is_tabular_base(base) for base in cls.bases):
                entries.append((path, cls))
    return entries


def _method_map(cls: ast.ClassDef) -> dict[str, ast.FunctionDef]:
    return {node.name: node for node in cls.body if isinstance(node, ast.FunctionDef)}


def _assert_method_present_and_signature(
    method_name: str,
    expected_param_types: list[list[str]],
    expected_return_types: list[str],
) -> None:
    for path, cls in _synth_classes():
        methods = _method_map(cls)
        method = methods.get(method_name)
        assert method is not None, f"{path.name}:{cls.name} missing method {method_name}"

        params = _get_params(method)[1:]
        assert len(params) == len(expected_param_types), (
            f"{path.name}:{cls.name}.{method_name} "
            f"expected {len(expected_param_types)} parameters, got {len(params)}"
        )

        for param, expected_parts in zip(params, expected_param_types):
            annotation = _annotation_to_str(param.annotation)
            assert annotation, f"{path.name}:{cls.name}.{method_name} missing param type"
            assert _annotation_matches(annotation, expected_parts), (
                f"{path.name}:{cls.name}.{method_name} "
                f"param type '{annotation}' does not include {expected_parts}"
            )

        return_annotation = _annotation_to_str(method.returns)
        assert return_annotation, (
            f"{path.name}:{cls.name}.{method_name} missing return type"
        )

        method_expected_returns = expected_return_types
        if method_name == "_load_model":
            method_expected_returns = expected_return_types + [cls.name]

        assert _annotation_matches(return_annotation, method_expected_returns), (
            f"{path.name}:{cls.name}.{method_name} "
            f"return type '{return_annotation}' does not include {method_expected_returns}"
        )


def test_algorithm_wrapper_files_present():
    assert _algorithm_files(), "No algorithm wrappers found"


def test_wrapper_has_tabular_synthesizer_subclass():
    for path in _algorithm_files():
        tree = ast.parse(path.read_text(encoding="utf-8"))
        classes = [node for node in tree.body if isinstance(node, ast.ClassDef)]
        synth_classes = [
            cls for cls in classes if any(_is_tabular_base(base) for base in cls.bases)
        ]
        assert synth_classes, f"{path.name} must define a TabularDataSynthesizer subclass"


def test_method_initialize_anonymization_configuration_definition():
    _assert_method_present_and_signature(
        "_initialize_anonymization_configuration",
        [["Dict", "dict"]],
        ["None"],
    )


def test_method_initialize_attribute_configuration_definition():
    _assert_method_present_and_signature(
        "_initialize_attribute_configuration",
        [["Dict", "dict"]],
        ["None"],
    )


def test_method_initialize_dataset_definition():
    _assert_method_present_and_signature(
        "_initialize_dataset",
        [["DataFrame"]],
        ["None"],
    )


def test_method_initialize_synthesizer_definition():
    _assert_method_present_and_signature(
        "_initialize_synthesizer",
        [],
        ["None"],
    )


def test_method_fit_definition():
    _assert_method_present_and_signature(
        "_fit",
        [],
        ["None"],
    )


def test_method_sample_definition():
    _assert_method_present_and_signature(
        "_sample",
        [],
        ["DataFrame"],
    )


def test_method_get_model_definition():
    _assert_method_present_and_signature(
        "_get_model",
        [],
        ["bytes"],
    )


def test_method_load_model_definition():
    _assert_method_present_and_signature(
        "_load_model",
        [["str", "Path"]],
        ["TabularDataSynthesizer"],
    )


def test_method_save_data_definition():
    _assert_method_present_and_signature(
        "_save_data",
        [["DataFrame"], ["str", "Path"]],
        ["None"],
    )
