import logging

import pandas as pd

from cinnamon_text_anonymization.inference import utils
from cinnamon_text_anonymization.inference.inference_model import InferenceModel

logger = logging.getLogger(__name__)

ANONYMIZATION_MODES = {"redact", "pseudonymize"}

BATCH_SIZE = 16
DEFAULT_CONFIDENCE_THRESHOLD = 0.90


def get_ner_model(model_type: str,
                  batch_size: int = BATCH_SIZE,
                  device: str | None = "auto") -> InferenceModel:
    """Loads a NER model for inference.

    Args:
        model_type (str): Model type used to select a path from `MODEL_PATHS`.
        batch_size (int): Number of text chunks processed together during inference.
        device (str | None): Device used for inference. Can be "auto", "cpu", "cuda", "cuda:N".

    Returns:
        The configured NER model.
    """
    model_path = utils.resolve_model_directory(model_type)
    return InferenceModel(
        model_path=model_path,
        batch_size=batch_size,
        device=device,
    )


def apply_anonymization_mode(text: str, entities: list[dict], anonymization_mode: str) -> str:
    """Replaces detected entity spans according to the anonymization mode.

    Args:
        text (str): Original text.
        entities (list[dict]): Detected entities with labels and character offsets.
        anonymization_mode (str): The transformation applied to detected entities,
            can be "pseudonymize" or "redact".

    Returns:
        The transformed text.
    """
    if anonymization_mode == "pseudonymize":
        logger.warning(
            "Pseudonymization is not implemented yet; falling back to redaction."
        )

    result = text

    for entity in sorted(
            entities,
            key=lambda item: item["start"],
            reverse=True,
    ):
        replacement = f"[{entity['label']}]"

        result = result[: entity["start"]] + replacement + result[entity["end"] :]

    return result


def process_texts(texts: list[str],
                  ner_model: InferenceModel,
                  confidence_threshold: float,
                  anonymization_mode: str) -> list[str]:
    """Runs NER and anonymization on a batch of texts.

    Args:
        texts (list[str]): Text values to anonymize.
        ner_model (InferenceModel): NER model used for entity detection.
        confidence_threshold (float): Minimum entity confidence score to accept.
        anonymization_mode (str): Transformation applied to detected entities.

    Returns:
        The anonymized texts in the same order as the input texts.
    """
    if not texts:
        return []

    predictions = ner_model.predict(texts)

    anonymized_texts = []

    for text, entities in zip(texts, predictions, strict=True):
        filtered_entities = [
            entity
            for entity in entities
            if entity["score"] >= confidence_threshold
        ]

        anonymized_texts.append(
            apply_anonymization_mode(
                text,
                filtered_entities,
                anonymization_mode,
            )
        )

    return anonymized_texts



def process_column(data: pd.DataFrame,
                   column_position: int,
                   result: pd.DataFrame,
                   ner_model: InferenceModel,
                   confidence_threshold: float,
                   anonymization_mode: str) -> None:
    """Processes the values of `column_position` in `data`.

    Args:
        data (pd.DataFrame): The input data to be processed.
        column_position (int): The position of the column in `data` to be processed.
        result (pd.DataFrame): The result data (a copy of `data`) where the result text will be stored).
        ner_model (InferenceModel): The model used for named entity recognition (NER).
        confidence_threshold (float): Minimum entity confidence score to accept.
        anonymization_mode (str): Transformation applied to detected entities.
    """
    column = data.iloc[:, column_position]

    mask = column.notna() & column.astype(str).ne("")

    texts = column[mask].astype(str).tolist()

    anonymized_texts = process_texts(
        texts=texts,
        ner_model=ner_model,
        confidence_threshold=confidence_threshold,
        anonymization_mode=anonymization_mode,
    )

    result.loc[mask, result.columns[column_position]] = anonymized_texts


def anonymize_data(data: pd.DataFrame,
                   target_columns: list[str],
                   ner_model: InferenceModel,
                   confidence_threshold: float,
                   anonymization_mode: str) -> pd.DataFrame:
    """Anonymizes configured text columns in a DataFrame.

    Args:
        data (pd.DataFrame): DataFrame containing the text to anonymize.
        target_columns (list[str]): Column names to anonymize.
        ner_model (InferenceModel): NER model used for entity detection.
        confidence_threshold (float): Minimum entity confidence score to accept.
        anonymization_mode (str): Transformation applied to detected entities.

    Returns:
        A copy of `data` with anonymized `target_columns`.
    """
    if not 0 <= confidence_threshold <= 1:
        raise ValueError(
            "confidence_threshold must be between 0 and 1."
        )

    result = data.copy(deep=True)

    if data.empty:
        return result

    for column_name in target_columns:
        if column_name not in data.columns:
            logger.warning("Configured text column %r is not present in the DataFrame.",column_name)
            continue

        column_position = data.columns.get_loc(column_name)

        if not isinstance(column_position, int):
            raise ValueError(f"Column name must be unique: {column_name}")

        process_column(data=data,
                        column_position=column_position,
                        result=result,
                        ner_model=ner_model,
                        confidence_threshold=confidence_threshold,
                        anonymization_mode=anonymization_mode)

    return result


def run_anonymization(config: dict, data: pd.DataFrame) -> pd.DataFrame:
    """Runs text anonymization according to the provided configuration.

    Args:
        config (dict): Text anonymization configuration.
        data (pd.DataFrame): DataFrame containing the data to anonymize.

    Returns:
        The anonymized DataFrame.
    """
    logger.info("Starting anonymization job with %s rows.",data.shape[0])

    target_columns = config.get("columns", [])

    if not target_columns:
        logger.info("No text columns configured for anonymization.")
        return data

    model_type = config.get("modelType")
    anonymization_mode = config.get("anonymizationMode")

    if anonymization_mode not in ANONYMIZATION_MODES:
        raise ValueError(
            f"Unsupported anonymization mode {anonymization_mode}. "
            f"Expected one of {ANONYMIZATION_MODES}."
        )

    confidence_threshold = float(config.get("confidenceThreshold", DEFAULT_CONFIDENCE_THRESHOLD))

    ner_model = get_ner_model(model_type=str(model_type), batch_size=BATCH_SIZE)

    logger.info("Using model type=%s from %s (window_size=%s, overlap=%s, batch_size=%s).",
                model_type,
                ner_model.model_path,
                ner_model.window_size,
                ner_model.overlap,
                BATCH_SIZE)

    return anonymize_data(data=data,
                          target_columns=target_columns,
                          ner_model=ner_model,
                          confidence_threshold=confidence_threshold,
                          anonymization_mode=anonymization_mode)
