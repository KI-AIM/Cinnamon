from __future__ import annotations

import math
import re
from dataclasses import dataclass
from typing import Any, Dict, Optional

from data_processing.utils import MISSING_VALUE_STRING
from synthetic_tabular_data_generator.embedding_profiles import (
    EmbeddingProfileConfig,
    embed_ollama_texts,
    load_embedding_profile_config,
)


SparseVector = dict[int, float]
DenseVector = list[float]


@dataclass(slots=True)
class _SparseTextEncoder:
    vocabulary: dict[str, int]

    @classmethod
    def from_texts(cls, texts: list[str]) -> "_SparseTextEncoder":
        vocabulary: dict[str, int] = {}
        for text in texts:
            for token in cls._tokenize(text):
                if token not in vocabulary:
                    vocabulary[token] = len(vocabulary)
        return cls(vocabulary=vocabulary)

    @staticmethod
    def _tokenize(text: str) -> list[str]:
        return re.findall(r"\w+", text.lower())

    def encode(self, text: str) -> SparseVector:
        counts: SparseVector = {}
        for token in self._tokenize(text):
            token_id = self.vocabulary.get(token)
            if token_id is None:
                continue
            counts[token_id] = counts.get(token_id, 0.0) + 1.0
        return counts


def _sparse_dot(u: SparseVector, v: SparseVector) -> float:
    if not u or not v:
        return 0.0

    dot_prod = sum(u[idx] * v[idx] for idx in u.keys() & v.keys())
    return dot_prod / (1.0 + dot_prod)


def _sparse_cosine(u: SparseVector, v: SparseVector) -> float:
    if not u or not v:
        return 0.0
    dot_prod = sum(u[idx] * v[idx] for idx in u.keys() & v.keys())
    return max(0.0, min(1.0, (dot_prod + 1.0) / 2.0))


def _cosine(u: DenseVector, v: DenseVector) -> float:
    if not u or not v:
        return 0.0
    dot_prod = sum(a * b for a, b in zip(u, v, strict=True))
    return max(0.0, min(1.0, (dot_prod + 1.0) / 2.0))


def _dot(u: DenseVector, v: DenseVector) -> float:
    if not u or not v:
        return 0.0
    dot_prod = sum(a * b for a, b in zip(u, v, strict=True))
    return 0.5 + 0.5 * dot_prod / (1.0 + abs(dot_prod))


def _angular(u: DenseVector, v: DenseVector) -> float:
    if not u or not v:
        return 0.0
    dot_prod = sum(a * b for a, b in zip(u, v, strict=True))
    return 1.0 - math.acos(max(-1.0, min(1.0, dot_prod))) / math.pi


def _euclidean(u: DenseVector, v: DenseVector) -> float:
    return 1.0 / (1.0 + math.sqrt(sum((a - b) * (a - b) for a, b in zip(u, v, strict=True))))


def _manhattan(u: DenseVector, v: DenseVector) -> float:
    return 1.0 / (1.0 + sum(abs(a - b) for a, b in zip(u, v, strict=True)))


class RetrievalSupport:
    """Shared retrieval operations; no synthesizer lifecycle or generation methods."""

    _SUPPORTED_EMBEDDING_PROVIDERS = {"term_frequency", "ollama"}
    _SUPPORTED_SPARSE_SIMILARITY_FUNCTIONS = {"sparse_cosine", "sparse_dot"}
    _SUPPORTED_DENSE_SIMILARITY_FUNCTIONS = {"cosine", "dot", "angular", "euclidean", "manhattan"}

    def _initialize_retrieval(self) -> None:
        self._query_vectors = {}
        self._few_shot_examples = 3
        self._embedding_provider = "term_frequency"
        self._similarity_function = "sparse_cosine"
        self._exclude_self_match = True
        self._reference_rows: list[dict[str, Any]] = []
        self._reference_vectors: list[Any] = []
        self._sparse_encoder: Optional[_SparseTextEncoder] = None
        self._embedding_profile: Optional[EmbeddingProfileConfig] = None

    def _configure_retrieval(self, config: Dict[str, Any]) -> None:
        algorithm_config = config["synthetization_configuration"]["algorithm"]
        model_params = algorithm_config.get("model_parameter", {})

        self._few_shot_examples = max(0, int(model_params.get("few_shot_examples", 3)))
        self._embedding_provider = str(model_params.get("embedding_provider", "term_frequency")).strip().lower()
        if self._embedding_provider not in self._SUPPORTED_EMBEDDING_PROVIDERS:
            raise ValueError(
                "embedding_provider must be one of: term_frequency, ollama."
            )

        default_similarity = "sparse_cosine" if self._embedding_provider == "term_frequency" else "cosine"
        self._similarity_function = str(model_params.get("similarity_function", default_similarity)).strip().lower()
        if self._embedding_provider == "term_frequency":
            if self._similarity_function not in self._SUPPORTED_SPARSE_SIMILARITY_FUNCTIONS:
                raise ValueError(
                    "For term_frequency retrieval, similarity_function must be one of: sparse_cosine, sparse_dot."
                )
            self._embedding_profile = None
        else:
            if self._similarity_function not in self._SUPPORTED_DENSE_SIMILARITY_FUNCTIONS:
                raise ValueError(
                    "For ollama retrieval, similarity_function must be one of: cosine, dot, angular, euclidean, manhattan."
                )
            self._embedding_profile = load_embedding_profile_config(config)

        self._exclude_self_match = self._parse_bool_like(model_params.get("exclude_self_match", True))

    def _fit_embedding_index(self) -> None:

        if self.dataset is None:
            raise ValueError("Dataset is not initialized.")

        self._query_vectors = {}
        reference_df = self.reference_dataset if self.reference_dataset is not None else self.dataset
        text_column = self._text_columns[0]
        reference_rows = []
        for row in reference_df.to_dict(orient="records"):
            text = self.coerce_text(row.get(text_column))
            if text == MISSING_VALUE_STRING:
                continue
            reference_rows.append(row)

        self._reference_rows = self.deduplicate_reference_rows(reference_rows, text_column)
        reference_texts = [self.coerce_text(row.get(text_column)) for row in self._reference_rows]
        self._reference_vectors = []
        self._sparse_encoder = None

        if not reference_texts:
            return

        if self._embedding_provider == "term_frequency":
            self._build_sparse_reference_index(reference_texts)
            return

        self._build_ollama_reference_index(reference_texts)

    def _build_sparse_reference_index(self, reference_texts: list[str]) -> None:
        encoder = _SparseTextEncoder.from_texts(reference_texts)
        self._sparse_encoder = encoder
        self._reference_vectors = [self._prepare_vector(encoder.encode(text)) for text in reference_texts]

    def _build_ollama_reference_index(self, reference_texts: list[str]) -> None:
        self._reference_vectors = [self._prepare_vector(vector) for vector in self._embed_ollama_texts(reference_texts)]

    def _prepare_vector(self, vector):
        if self._similarity_function not in {"sparse_cosine", "cosine", "angular"}:
            return vector
        values = vector.values() if isinstance(vector, dict) else vector
        norm = math.sqrt(sum(value * value for value in values))
        if not norm:
            return {} if isinstance(vector, dict) else []
        if isinstance(vector, dict):
            return {key: value / norm for key, value in vector.items()}
        return [value / norm for value in vector]

    def _retrieval_query(self, row):
        return self.coerce_text(row.get(self._text_columns[0]))

    def _generation_rows(self, rows):
        # Bound query cache and HTTP batches independently of the total output size.
        try:
            for start in range(0, len(rows), 64):
                batch = rows[start:start + 64]
                self._query_vectors = {}
                if (self._embedding_provider == "ollama" and self._few_shot_examples > 0
                        and self._reference_vectors and getattr(self, "_text_similarity_weight", 1) > 0):
                    queries = list(dict.fromkeys(self._retrieval_query(row) for row in batch))
                    queries = [query for query in queries if query != MISSING_VALUE_STRING]
                    vectors = self._embed_ollama_texts(queries)
                    self._query_vectors = dict(zip(queries, map(self._prepare_vector, vectors), strict=True))
                yield from batch
        finally:
            self._query_vectors = {}

    def _encode_query(self, query_text: str) -> Any:
        if self._embedding_provider == "term_frequency":
            return self._prepare_vector(self._sparse_encoder.encode(query_text)) if self._sparse_encoder else {}
        if query_text in self._query_vectors:
            return self._query_vectors[query_text]
        vectors = self._embed_ollama_texts([query_text])
        return self._prepare_vector(vectors[0]) if vectors else []

    def _score_vectors(self, query_vector: Any, reference_vector: Any) -> float:
        if self._embedding_provider == "term_frequency":
            return self._score_sparse_vectors(query_vector, reference_vector)
        return self._score_dense_vectors(query_vector, reference_vector)

    def _score_sparse_vectors(self, query_vector: SparseVector, reference_vector: SparseVector) -> float:
        if self._similarity_function == "sparse_dot":
            return _sparse_dot(query_vector, reference_vector)
        return _sparse_cosine(query_vector, reference_vector)

    def _score_dense_vectors(self, query_vector: DenseVector, reference_vector: DenseVector) -> float:
        if self._similarity_function == "dot":
            return _dot(query_vector, reference_vector)
        if self._similarity_function == "angular":
            return _angular(query_vector, reference_vector)
        if self._similarity_function == "euclidean":
            return _euclidean(query_vector, reference_vector)
        if self._similarity_function == "manhattan":
            return _manhattan(query_vector, reference_vector)
        return _cosine(query_vector, reference_vector)

    def _embed_ollama_texts(self, texts: list[str]) -> list[DenseVector]:
        if self._embedding_profile is None:
            raise ValueError("Ollama embedding profile is not initialized.")
        vectors = []
        for start in range(0, len(texts), 64):
            batch = embed_ollama_texts(self._embedding_profile, texts[start:start + 64])
            for vector in batch:
                if not vector or not all(math.isfinite(value) for value in vector):
                    raise ValueError("Embedding vectors must be non-empty and finite.")
                if vectors and len(vector) != len(vectors[0]):
                    raise ValueError("Embedding vectors must have equal dimensions.")
                vectors.append(vector)
        return vectors
