from types import SimpleNamespace

import cloudpickle
import pandas as pd
import pytest

from synthetic_tabular_data_generator.algorithms.llm_tabular import LlmTabularSynthesizer
from synthetic_tabular_data_generator.algorithms.llm_text_only_paraphrase_synthesis import LlmTextOnlyParaphraseSynthesisSynthesizer
from synthetic_tabular_data_generator.algorithms.llm_text_only_embedding_nearest_neighbor_synthesis import LlmTextOnlyEmbeddingNearestNeighborSynthesisSynthesizer
from synthetic_tabular_data_generator.algorithms.llm_mixed_data_paraphrase_synthesis import LlmMixedDataParaphraseSynthesisSynthesizer
from synthetic_tabular_data_generator.algorithms.llm_mixed_data_expert_guided_synthesis import LlmMixedDataExpertGuidedSynthesisSynthesizer
from synthetic_tabular_data_generator.algorithms.llm_mixed_data_embedding_nearest_neighbor_synthesis import LlmMixedDataEmbeddingNearestNeighborSynthesisSynthesizer
from synthetic_tabular_data_generator.tabular_data_synthesizer import SynthesizerOperationError


@pytest.mark.parametrize("cls,kinds", [
    (LlmTabularSynthesizer, ["INTEGER"]),
    (LlmTextOnlyParaphraseSynthesisSynthesizer, ["TEXT"]),
    (LlmTextOnlyEmbeddingNearestNeighborSynthesisSynthesizer, ["TEXT"]),
    (LlmMixedDataParaphraseSynthesisSynthesizer, ["INTEGER", "TEXT"]),
    (LlmMixedDataExpertGuidedSynthesisSynthesizer, ["INTEGER", "TEXT"]),
    (LlmMixedDataEmbeddingNearestNeighborSynthesisSynthesizer, ["INTEGER", "TEXT"]),
])
def test_llm_export_excludes_credentials_and_all_reference_state(cls, kinds, tmp_path):
    synth = cls()
    attrs = {"configurations": [{"name": f"c{i}", "type": kind} for i, kind in enumerate(kinds)]}
    synth.initialize_attribute_configuration(attrs)
    source = pd.DataFrame({"text": ["EXPORT_PRIVATE_SOURCE"]})
    synth.dataset = source
    synth.reference_dataset = source
    synth._profile_data = source
    synth._reference_texts = ["EXPORT_PRIVATE_SOURCE"]
    synth._reference_rows = [{"text": "EXPORT_PRIVATE_SOURCE"}]
    synth._column_profiles = {"text": {"top_values": ["EXPORT_PRIVATE_SOURCE"]}}
    synth._prompt_prefix = "EXPORT_PRIVATE_SOURCE"
    synth._generation_prompt_prefix = "EXPORT_PRIVATE_SOURCE"
    synth._expert_guidance = "EXPORT_PRIVATE_SOURCE"
    synth._sparse_encoder = {"EXPORT_PRIVATE_SOURCE": 1}
    synth._structured_similarity_functions = {"text": lambda *_: "EXPORT_PRIVATE_SOURCE"}
    synth._llm_config = SimpleNamespace(api_key="EXPORT_PRIVATE_KEY")
    synth._llm_client = SimpleNamespace(config=synth._llm_config, logger="EXPORT_PRIVATE_SOURCE")
    synth.anonymization_configuration = {"nested": {"api_key": "EXPORT_PRIVATE_KEY"}}
    synth._sampling = {"api_key": "EXPORT_PRIVATE_KEY"}
    synth.set_progress_callback(lambda *_: "EXPORT_PRIVATE_CALLBACK")
    synth._anonymization_configuration_initialized = True
    synth._dataset_initialized = True
    synth._fit_completed = True
    synth._synthesizer_initialized = True

    blob = synth.get_model()
    for marker in (b"EXPORT_PRIVATE_SOURCE", b"EXPORT_PRIVATE_KEY", b"EXPORT_PRIVATE_CALLBACK"):
        assert marker not in blob
    path = tmp_path / "model.pkl"
    path.write_bytes(blob)
    restored = synth.load_model(str(path))
    assert isinstance(restored, cls)
    assert restored.attribute_configuration == attrs
    assert restored.dataset is None
    assert restored._llm_client is None
    assert restored._llm_config is None
    assert restored._progress_callback is None
    assert not restored.is_ready_for_sampling
    with pytest.raises(SynthesizerOperationError, match="initialized"):
        restored.sample()
    assert synth.dataset is source
    assert synth._llm_config.api_key == "EXPORT_PRIVATE_KEY"
    assert synth.is_ready_for_sampling

    # The two-stage callback retains its existing nested-bytes format.
    bundle = cloudpickle.loads(cloudpickle.dumps({"text_synthesis": {"text": blob}}))
    assert isinstance(cloudpickle.loads(bundle["text_synthesis"]["text"]), cls)
