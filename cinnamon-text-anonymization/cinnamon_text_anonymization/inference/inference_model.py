from __future__ import annotations

from pathlib import Path
from typing import Any

from transformers import AutoModelForTokenClassification, AutoTokenizer, pipeline

from cinnamon_text_anonymization.inference import utils


def token_windows(length: int,window_size: int, overlap: int) -> list[tuple[int, int]]:
    """Creates overlapping windows over a token sequence.

    Args:
        length: Total number of tokens in the sequence.
        window_size: Maximum number of tokens in each window.
        overlap: Number of tokens shared between consecutive windows.

    Returns:
        A list of token (start, end) index pairs.
    """
    if window_size <= 0:
        raise ValueError("window_size must be positive")

    if overlap < 0 or overlap >= window_size:
        raise ValueError("overlap must be >= 0 and smaller than window_size")

    step = window_size - overlap

    return [
        (start, min(start + window_size, length))
        for start in range(0, length, step)
        if start < length
    ]

def merge_entities(entities: list[dict[str, Any]], text: str) -> list[dict[str, Any]]:
    """Merges overlapping entities into one.

    Args:
        entities: The list of overlapping entities for the given `text`.
        text: The original text the entities belong to.

    Returns:
        The list of the merged, unique entities.
    """
    se = lambda e: (int(e["start"]), int(e["end"]))
    entities = sorted(
        ({
            "label": entity["entity_group"],
            "score": float(entity["score"]),
            "text": text[se(entity)[0]:se(entity)[1]],
            "start": se(entity)[0],
            "end": se(entity)[1],
        } for entity in entities),
        key=lambda entity: (entity["start"], -entity["end"]),
    )

    merged: list[dict[str, Any]] = []

    for entity in entities:
        if not entity["label"]:
            continue

        if not merged:
            merged.append(entity)
            continue

        previous = merged[-1]

        if entity["start"] < previous["end"]:
            # Same-label overlapping predictions usually come from
            # overlapping inference windows.
            if entity["label"] == previous["label"]:
                previous_length = previous["end"] - previous["start"]
                current_length = entity["end"] - entity["start"]

                if current_length > previous_length:
                    merged[-1] = entity
                elif current_length == previous_length and entity["score"] > previous["score"]:
                    merged[-1] = entity

            elif entity["score"] > previous["score"]:
                merged[-1] = entity

            continue

        merged.append(entity)

    return merged




class InferenceModel:
    """A wrapper inference model for a token-classification model."""

    def __init__(self,
                 model_path: Path,
                 device: str | None = "auto",
                 batch_size: int = utils.BATCH_SIZE) -> None:
        """Initializes the NER inference model.

        Args:
            model_path (Path): Path to the Hugging Face model.
            device (str | None): Inference device. Can be "auto", "cpu", "cuda", "cuda:N".
            batch_size (int): Number of inputs processed together by the Hugging Face pipeline.
        """

        self.model_path = model_path
        self.batch_size = batch_size
        self.device = utils.resolve_device(device)

        self.window_size, self.overlap = utils.load_inference_config(self.model_path)

        self.tokenizer = AutoTokenizer.from_pretrained(self.model_path)
        self.model = AutoModelForTokenClassification.from_pretrained(self.model_path)
        self.ner = pipeline(
            "token-classification",
            model=self.model,
            tokenizer=self.tokenizer,
            aggregation_strategy="simple",
            device=self.device,
        )

    def predict(self, texts: list[str]) -> list[list[dict[str, Any]]]:
        """Runs NER on texts, using overlapping windows for long documents.

        Documents that fit within `self.window_size` are sent directly to the
        NER pipeline. Longer documents are split into overlapping token
        windows. Window-relative entity offsets are converted back to
        document-relative offsets and duplicate predictions are resolved.

        Args:
            texts: Documents to process.

        Returns:
            A list of entity predictions, one for each input text.
        """
        if not texts:
            return []

        encodings = [
            self.tokenizer(
                text,
                add_special_tokens=False,
                return_offsets_mapping=True,
                truncation=False,
            )
            for text in texts
        ]

        entities_by_text: list[list[dict[str, Any]]] = [[] for _ in texts]

        window_requests: list[tuple[int, int, str]] = []

        for document_index, encoding in enumerate(encodings):
            token_count = len(encoding["input_ids"])

            offsets = encoding["offset_mapping"]
            overlapping_windows = token_windows(length=token_count, window_size=self.window_size, overlap=self.overlap)

            for token_start, token_end in overlapping_windows:
                # Extract the token window boundaries to character offsets in the original text
                char_start = offsets[token_start][0]
                char_end = offsets[token_end - 1][1]

                window_requests.append((int(document_index),
                                        int(char_start),
                                        str(texts[document_index][char_start:char_end])))

        window_texts = [window_text for _, _, window_text in window_requests]

        if len(window_texts) == 0:
            return []

        window_results = self.ner(window_texts, batch_size=self.batch_size)

        for (document_index, char_start, _), entities in zip(window_requests, window_results, strict=True):
            for entity in entities:
                # Add the character offset in the original text
                entity["start"] = int(entity["start"]) + char_start
                entity["end"] = int(entity["end"]) + char_start

                entities_by_text[document_index].append(entity)

        return [
            merge_entities(entities, text)
            for entities, text in zip(
                entities_by_text,
                texts,
                strict=True,
            )
        ]
