import math
from types import SimpleNamespace

import pandas as pd
import pytest

from synthetic_tabular_data_generator.llm import retrieval
from synthetic_tabular_data_generator.algorithms.llm_text_only_embedding_nearest_neighbor_synthesis import (
    LlmTextOnlyEmbeddingNearestNeighborSynthesisSynthesizer as TextRetrieval,
)
from synthetic_tabular_data_generator.algorithms.llm_mixed_data_embedding_nearest_neighbor_synthesis import (
    LlmMixedDataEmbeddingNearestNeighborSynthesisSynthesizer as MixedRetrieval,
)


def test_dot_scores_preserve_order_above_one():
    assert 0 == retrieval._sparse_dot({0: 1}, {1: 1})
    assert 0 < retrieval._sparse_dot({0: 1}, {0: 1}) < retrieval._sparse_dot({0: 1}, {0: 3}) < 1
    scores = [retrieval._dot([2.0], [value]) for value in (-3, -1, 0, 1, 3)]
    assert scores == sorted(set(scores))
    assert all(0 < score < 1 for score in scores)


@pytest.mark.parametrize("metric", ["cosine", "angular", "euclidean", "manhattan", "dot"])
def test_dense_metrics_after_preparation_match_definitions(metric):
    synth = TextRetrieval()
    synth._embedding_provider = "ollama"
    synth._similarity_function = metric
    u, v = [3.0, 4.0], [-2.0, 1.0]
    dot = sum(a * b for a, b in zip(u, v))
    cosine = dot / (5 * math.sqrt(5))
    expected = {
        "cosine": (cosine + 1) / 2,
        "angular": 1 - math.acos(cosine) / math.pi,
        "euclidean": 1 / (1 + math.sqrt(34)),
        "manhattan": 1 / 9,
        "dot": 0.5 + 0.5 * dot / (1 + abs(dot)),
    }
    assert synth._score_vectors(synth._prepare_vector(u), synth._prepare_vector(v)) == pytest.approx(expected[metric])


def test_sparse_top_k_ties_preserve_reference_order():
    synth = TextRetrieval()
    synth._configure_retrieval({"synthetization_configuration": {"algorithm": {"model_parameter": {
        "embedding_provider": "term_frequency", "few_shot_examples": 2,
    }}}})
    synth._text_columns = ["text"]
    synth.dataset = pd.DataFrame({"text": ["diabetes"]})
    synth.reference_dataset = pd.DataFrame({"text": ["diabetes first", "diabetes second", "diabetes third", "unrelated"]})
    synth._fit_embedding_index()
    assert synth._embedding_provider == "term_frequency"
    assert synth._neighbor_examples({"text": "diabetes"}) == ["diabetes first", "diabetes second"]
    assert synth._neighbor_examples({"text": None}) == []
    assert synth._neighbor_examples({"text": "unknown"}) == []


def test_datetime_distance_and_reference_normalization_once(monkeypatch):
    synth = MixedRetrieval()
    assert not isinstance(synth, TextRetrieval)
    configs = [{"name": "visit", "type": "DATE_TIME", "configurations": [{"dateTimeFormatter": "yyyy-MM-dd HH:mm:ss"}]},
               {"name": "text", "type": "TEXT"}]
    synth.initialize_attribute_configuration({"configurations": configs})
    synth.dataset = pd.DataFrame({"visit": ["2024-01-01 00:00:00"], "text": [None]})
    synth.reference_dataset = pd.DataFrame({"visit": ["2024-01-01 00:00:00", "2024-01-03 00:00:00"],
                                            "text": ["early", "late"]})
    synth._text_similarity_weight = 0
    synth._structured_similarity_weight = 1
    synth._few_shot_examples = 1
    synth._fit()
    calls = []
    original = synth._normalize_structured_similarity_value

    def normalize(config, value):
        calls.append(value)
        return original(config, value)

    monkeypatch.setattr(synth, "_normalize_structured_similarity_value", normalize)
    assert synth._neighbor_examples({"visit": "2024-01-02 23:00:00", "text": None}) == ["late"]
    assert calls == ["2024-01-02 23:00:00"]


def test_embedding_batches_cache_queries_and_clear_on_refit(monkeypatch):
    synth = TextRetrieval()
    synth._embedding_provider = "ollama"
    synth._similarity_function = "cosine"
    synth._embedding_profile = SimpleNamespace()
    synth._text_columns = ["text"]
    calls = []

    def embed(profile, texts):
        calls.append(list(texts))
        return [[3.0, 4.0] for _ in texts]

    monkeypatch.setattr(retrieval, "embed_ollama_texts", embed)
    synth.dataset = pd.DataFrame({"text": [f"text {i}" for i in range(130)]})
    synth.reference_dataset = None
    synth._fit_embedding_index()
    assert list(map(len, calls)) == [64, 64, 2]
    calls.clear()
    rows = [{"text": f"query {i // 2}"} for i in range(130)]
    seen = []
    for row in synth._generation_rows(rows):
        seen.append(row)
        assert synth._encode_query(row["text"]) == pytest.approx([0.6, 0.8])
        assert len(synth._query_vectors) <= 64
    assert seen == rows
    assert list(map(len, calls)) == [32, 32, 1]
    assert not synth._query_vectors
    synth._query_vectors = {"stale": [1, 0]}
    synth._fit_embedding_index()
    assert not synth._query_vectors


@pytest.mark.parametrize("vector", [[], [float("nan")], [float("inf")]])
def test_invalid_embedding_vectors_are_rejected(monkeypatch, vector):
    synth = TextRetrieval()
    synth._embedding_profile = SimpleNamespace()
    monkeypatch.setattr(retrieval, "embed_ollama_texts", lambda *args: [vector])
    with pytest.raises(ValueError, match="non-empty and finite"):
        synth._embed_ollama_texts(["text"])


def test_sparse_cosine_keeps_normalized_scores_and_zero_behavior():
    synth = TextRetrieval()
    u, v = {0: 3.0, 1: 4.0}, {0: 2.0, 2: 1.0}
    expected = (6 / (5 * math.sqrt(5)) + 1) / 2
    assert synth._score_vectors(synth._prepare_vector(u), synth._prepare_vector(v)) == pytest.approx(expected)
    assert synth._score_vectors({}, synth._prepare_vector(v)) == 0


def test_attribute_only_generation_skips_embedding_requests(monkeypatch):
    synth = MixedRetrieval()
    synth._embedding_provider = "ollama"
    synth._text_similarity_weight = 0
    synth._reference_vectors = [None]
    monkeypatch.setattr(synth, "_embed_ollama_texts", lambda _: pytest.fail("attribute-only retrieval must not embed queries"))
    rows = [{"age": 42, "text": None}]
    assert list(synth._generation_rows(rows)) == rows


def test_query_cache_is_cleared_when_generation_stops_early(monkeypatch):
    synth = TextRetrieval()
    synth._embedding_provider = "ollama"
    synth._text_columns = ["text"]
    synth._reference_vectors = [[1, 0]]
    monkeypatch.setattr(synth, "_embed_ollama_texts", lambda texts: [[1, 0] for _ in texts])
    iterator = synth._generation_rows([{"text": "query"}])
    next(iterator)
    assert synth._query_vectors
    iterator.close()
    assert not synth._query_vectors
