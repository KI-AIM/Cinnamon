from __future__ import annotations

import json
import math
import re
from dataclasses import dataclass
from typing import Any, Dict, Optional

import pandas as pd
import requests

from data_processing.utils import MISSING_VALUE_STRING
from synthetic_tabular_data_generator.embedding_profiles import (
    EmbeddingProfileConfig,
    load_embedding_profile_config,
)
from synthetic_tabular_data_generator.algorithms.llm_text_only_semantic_variation_synthesis import (
    LlmTextOnlySemanticVariationSynthesisSynthesizer,
)


SparseVector = dict[int, float]
DenseVector = list[float]


@dataclass(slots=True)
class _FallbackSparseBm25Encoder:
    vocabulary: dict[str, int]

    @classmethod
    def from_texts(cls, texts: list[str]) -> "_FallbackSparseBm25Encoder":
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
    normalized = (dot_prod + 1.0) / 2.0
    return max(0.0, min(1.0, normalized))


def _sparse_cosine(u: SparseVector, v: SparseVector) -> float:
    if not u or not v:
        return 0.0

    dot_prod = sum(u[idx] * v[idx] for idx in u.keys() & v.keys())
    u_norm = math.sqrt(sum(val * val for val in u.values()))
    v_norm = math.sqrt(sum(val * val for val in v.values()))
    if u_norm == 0.0 or v_norm == 0.0:
        return 0.0

    cos_val = dot_prod / (u_norm * v_norm)
    cos_sim = (cos_val + 1.0) / 2.0
    return max(0.0, min(1.0, cos_sim))


def _cosine(u: DenseVector, v: DenseVector) -> float:
    if not u or not v:
        return 0.0
    dot_prod = sum(a * b for a, b in zip(u, v, strict=True))
    u_norm = math.sqrt(sum(a * a for a in u))
    v_norm = math.sqrt(sum(b * b for b in v))
    if u_norm == 0.0 or v_norm == 0.0:
        return 0.0
    cos_val = dot_prod / (u_norm * v_norm)
    cos_sim = (cos_val + 1.0) / 2.0
    return max(0.0, min(1.0, cos_sim))


def _dot(u: DenseVector, v: DenseVector) -> float:
    if not u or not v:
        return 0.0
    dot_prod = sum(a * b for a, b in zip(u, v, strict=True))
    normalized = (dot_prod + 1.0) / 2.0
    return max(0.0, min(1.0, normalized))


def _angular(u: DenseVector, v: DenseVector) -> float:
    if not u or not v:
        return 0.0
    u_norm = math.sqrt(sum(a * a for a in u))
    v_norm = math.sqrt(sum(b * b for b in v))
    if u_norm == 0.0 or v_norm == 0.0:
        return 0.0
    ratio = sum(a * b for a, b in zip(u, v, strict=True)) / (u_norm * v_norm)
    ratio = max(-1.0, min(1.0, ratio))
    return 1.0 - (math.acos(ratio) / math.pi)


def _euclidean(u: DenseVector, v: DenseVector) -> float:
    return 1.0 / (1.0 + math.sqrt(sum((a - b) * (a - b) for a, b in zip(u, v, strict=True))))


def _manhattan(u: DenseVector, v: DenseVector) -> float:
    return 1.0 / (1.0 + sum(abs(a - b) for a, b in zip(u, v, strict=True)))


class LlmTextOnlyEmbeddingNearestNeighborSynthesisSynthesizer(
    LlmTextOnlySemanticVariationSynthesisSynthesizer
):
    """
    Generate TEXT-only rows using sparse BM25-style nearest-neighbor retrieval as prompt context.
    """

    _SUPPORTED_EMBEDDING_PROVIDERS = {"bm25", "ollama"}
    _SUPPORTED_SPARSE_SIMILARITY_FUNCTIONS = {"sparse_cosine", "sparse_dot"}
    _SUPPORTED_DENSE_SIMILARITY_FUNCTIONS = {"cosine", "dot", "angular", "euclidean", "manhattan"}

    def __init__(
        self,
        attribute_configuration: Optional[Dict[str, Any]] = None,
        anonymization_configuration: Optional[Dict[str, Any]] = None,
    ) -> None:
        super().__init__(attribute_configuration, anonymization_configuration)
        self._few_shot_examples = 3
        self._embedding_provider = "bm25"
        self._similarity_function = "sparse_cosine"
        self._exclude_self_match = True
        self._reference_rows: list[dict[str, Any]] = []
        self._reference_vectors: list[Any] = []
        self._fallback_encoder: Optional[_FallbackSparseBm25Encoder] = None
        self._cbrkit_bm25_encoder: Any = None
        self._embedding_profile: Optional[EmbeddingProfileConfig] = None

    def _initialize_anonymization_configuration(self, config: Dict[str, Any]) -> None:
        super()._initialize_anonymization_configuration(config)
        algorithm_config = config["synthetization_configuration"]["algorithm"]
        model_params = algorithm_config.get("model_parameter", {})

        self._few_shot_examples = max(0, int(model_params.get("few_shot_examples", 3)))
        self._embedding_provider = str(model_params.get("embedding_provider", "bm25")).strip().lower()
        if self._embedding_provider not in self._SUPPORTED_EMBEDDING_PROVIDERS:
            raise ValueError(
                "embedding_provider must be one of: bm25, ollama."
            )

        default_similarity = "sparse_cosine" if self._embedding_provider == "bm25" else "cosine"
        self._similarity_function = str(model_params.get("similarity_function", default_similarity)).strip().lower()
        if self._embedding_provider == "bm25":
            if self._similarity_function not in self._SUPPORTED_SPARSE_SIMILARITY_FUNCTIONS:
                raise ValueError(
                    "For bm25 retrieval, similarity_function must be one of: sparse_cosine, sparse_dot."
                )
            self._embedding_profile = None
        else:
            if self._similarity_function not in self._SUPPORTED_DENSE_SIMILARITY_FUNCTIONS:
                raise ValueError(
                    "For ollama retrieval, similarity_function must be one of: cosine, dot, angular, euclidean, manhattan."
                )
            self._embedding_profile = load_embedding_profile_config(config)

        self._exclude_self_match = self._parse_bool_like(model_params.get("exclude_self_match", True))

    def _initialize_synthesizer(self) -> None:
        if self._fitting_kwargs is None:
            raise ValueError("Anonymization configuration must be initialized before synthesizer setup.")
        self._initialize_llm_backend(mode="text_only_embedding_nearest_neighbor")

    def _fit(self) -> None:
        super()._fit()

        if self.dataset is None:
            raise ValueError("Dataset is not initialized.")

        reference_df = self.reference_dataset if self.reference_dataset is not None else self.dataset
        text_column = self._text_columns[0]
        reference_rows = []
        reference_texts = []
        for row in reference_df.to_dict(orient="records"):
            text = self.coerce_text(row.get(text_column))
            if text == MISSING_VALUE_STRING:
                continue
            reference_rows.append(row)
            reference_texts.append(text)

        self._reference_rows = reference_rows
        self._reference_vectors = []
        self._fallback_encoder = None
        self._cbrkit_bm25_encoder = None

        if not reference_texts:
            return

        if self._embedding_provider == "bm25":
            self._build_sparse_reference_index(reference_texts)
            return

        self._build_ollama_reference_index(reference_texts)

    def _build_prompt_prefix(self) -> str:
        text_column = self._text_columns[0]
        domain_context = ""
        if self._user_prompt_domain_context:
            domain_context = f"Domain context: {self._user_prompt_domain_context}\n"

        required_attributes_block = ""
        if self._required_attributes:
            lines = [
                f"- {item['name']}: {item['description']}" if item["description"] else f"- {item['name']}"
                for item in self._required_attributes
            ]
            required_attributes_block = (
                "Required attributes that must be mentioned explicitly in the generated text:\n"
                + "\n".join(lines)
                + "\n"
            )

        return (
            "You generate a new TEXT value for a fictional patient based on a source table row and similar reference texts.\n"
            f"{domain_context}"
            "Information:\n"
            "- Create an independent new clinical case. Novelty from every individual input case is more important than preserving the source case.\n"
            "- Treat the source row and reference texts as pools of domain, content, and style signals, not as templates to rewrite.\n"
            "- Preserve only the medical domain, document type, language, tone, approximate length, and general level of clinical detail.\n"
            "- Do not preserve the source case's narrative blueprint, paragraph mapping, order of clinical events, or combination of case-specific details.\n"
            "- Before writing, silently construct a new coherent case plan containing the presentation, relevant history, findings, diagnosis, treatment, clinical course, complications, and outcome.\n"
            "- The new case plan must differ substantially from the source in at least five of these areas: presentation, chronology, medical history, findings, medication, treatment course, complications, and discharge plan.\n"
            "- Build a new chronology from scratch. Do not obtain a new timeline merely by shifting the original dates.\n"
            "- Do not copy exact combinations of diagnoses, metastasis sites, therapies, adverse events, examination findings, and outcomes from one input case.\n"
            "- Standard medical phrases, section headings, diagnoses, and technical terms may remain unchanged when appropriate.\n"
            "- Ensure that all dates are chronologically valid.\n"
            "- The document date must not precede the described discharge date.\n"
            "- Ensure that diagnoses, ICD codes, symptoms, medication, dosage, treatment, findings, and outcome are medically plausible and mutually consistent.\n"
            "- Do not add findings that are unrelated to the clinical course or remain unexplained.\n"
            "- Avoid impossible treatment periods, contradictory timelines, implausible dosages, and unsupported diagnoses.\n"
            "- Before returning the result, internally check: 1. chronology, 2. diagnosis and ICD consistency, 3. medication plausibility, 4. consistency between findings, treatment, and discharge.\n"
            "- Before returning the result, compare it with every input case. If its event sequence, paragraph structure, or case-specific wording is still recognizably derived from one case, redesign and rewrite it.\n"
            "- Do not mention that the text is synthetic, fictional, generated, anonymized, or paraphrased.\n"
            f"- Keep a missing TEXT value as '{MISSING_VALUE_STRING}'.\n"
            f"{required_attributes_block}"
            "Output rules:\n"
            "- Return ONLY valid JSON.\n"
            "- Use exactly this shape: {\"row\": { ... }}\n"
            f"- Include exactly this column in row: {text_column}\n"
            f"- Generate a value only for this TEXT column: {text_column}\n"
            f"- For a missing string/text use '{MISSING_VALUE_STRING}'\n"
            "\n"
        )

    def _build_rewrite_prompt(self, base_row: Dict[str, Any]) -> str:
        prompt_row = self.serialize_row_for_prompt(base_row, self._ordered_column_configs)
        reference_examples = self._neighbor_examples(base_row)
        reference_block = ""
        if reference_examples:
            example_lines = [
                f"{index + 1}. {json.dumps(example, ensure_ascii=False)}"
                for index, example in enumerate(reference_examples)
            ]
            reference_block = (
                "NEAREST-NEIGHBOR REFERENCE EXAMPLES\n"
                "----------------------------------------\n"
                f"{chr(10).join(example_lines)}\n\n"
            )

        return (
            f"{self._prompt_prefix or self._build_prompt_prefix()}"
            "SOURCE ROW\n"
            "----------------------------------------\n\n"
            f"{json.dumps({'row': prompt_row}, ensure_ascii=False, indent=2)}\n\n"
            f"{reference_block}"
        )

    def _neighbor_examples(self, base_row: Dict[str, Any]) -> list[str]:
        if self._few_shot_examples <= 0 or not self._reference_rows or not self._reference_vectors:
            return []

        text_column = self._text_columns[0]
        query_text = self.coerce_text(base_row.get(text_column))
        if query_text == MISSING_VALUE_STRING:
            return []

        query_vector = self._encode_query(query_text)
        if not query_vector:
            return []

        scored_examples: list[tuple[float, str]] = []
        for row, reference_vector in zip(self._reference_rows, self._reference_vectors):
            reference_text = self.coerce_text(row.get(text_column))
            if reference_text == MISSING_VALUE_STRING:
                continue
            if self._exclude_self_match and reference_text == query_text:
                continue
            score = self._score_vectors(query_vector, reference_vector)
            scored_examples.append((score, reference_text))

        scored_examples.sort(key=lambda item: item[0], reverse=True)
        return [text for _, text in scored_examples[: self._few_shot_examples]]

    def _build_sparse_reference_index(self, reference_texts: list[str]) -> None:
        # ponytail: use cbrkit[bm25] when available; keep a tiny local fallback so the feature works in the current image.
        try:
            from cbrkit.sim.embed import bm25 as cbrkit_bm25
        except Exception:
            encoder = _FallbackSparseBm25Encoder.from_texts(reference_texts)
            self._fallback_encoder = encoder
            self._reference_vectors = [encoder.encode(text) for text in reference_texts]
            return

        encoder = cbrkit_bm25(language="english")
        encoder.put_index(reference_texts)
        self._cbrkit_bm25_encoder = encoder
        self._reference_vectors = list(encoder(reference_texts))

    def _build_ollama_reference_index(self, reference_texts: list[str]) -> None:
        self._reference_vectors = self._embed_ollama_texts(reference_texts)

    def _encode_query(self, query_text: str) -> Any:
        if self._embedding_provider == "bm25":
            return self._encode_sparse_query(query_text)
        return self._encode_dense_query(query_text)

    def _encode_sparse_query(self, query_text: str) -> SparseVector:
        if self._cbrkit_bm25_encoder is not None:
            vectors = self._cbrkit_bm25_encoder([query_text])
            return vectors[0] if vectors else {}
        if self._fallback_encoder is None:
            return {}
        return self._fallback_encoder.encode(query_text)

    def _encode_dense_query(self, query_text: str) -> DenseVector:
        vectors = self._embed_ollama_texts([query_text])
        return vectors[0] if vectors else []

    def _score_vectors(self, query_vector: Any, reference_vector: Any) -> float:
        if self._embedding_provider == "bm25":
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
        if not texts:
            return []

        url = f"{self._embedding_profile.base_url}{self._embedding_profile.endpoint_path}"
        payload = {
            "model": self._embedding_profile.model_name,
            "input": texts,
        }
        last_error: Optional[Exception] = None
        for attempt_index in range(self._embedding_profile.max_retries):
            try:
                response = requests.post(
                    url,
                    json=payload,
                    timeout=self._embedding_profile.timeout_seconds,
                    verify=self._embedding_profile.verify_ssl,
                )
                response.raise_for_status()
                body = response.json()
                embeddings = body.get("embeddings")
                if not isinstance(embeddings, list) or not embeddings:
                    raise ValueError("Embedding response is empty or missing the 'embeddings' field.")
                return [
                    [float(value) for value in embedding]
                    for embedding in embeddings
                    if isinstance(embedding, list)
                ]
            except Exception as exc:  # noqa: BLE001
                last_error = exc
                if attempt_index + 1 >= self._embedding_profile.max_retries:
                    break
        if last_error is not None:
            raise RuntimeError("Unable to retrieve embeddings from the configured Ollama embedding profile.") from last_error
        raise RuntimeError("Unable to retrieve embeddings from the configured Ollama embedding profile.")
