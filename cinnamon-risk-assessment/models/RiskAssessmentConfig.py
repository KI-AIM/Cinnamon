from pydantic import BaseModel, Field
from typing import List, Optional


class LinkageConfig(BaseModel):
    n_attacks: int
    available_columns: List[str]
    unavailable_columns: List[str]


class SinglingOutConfig(BaseModel):
    n_attacks: int


class AttributeInferenceConfig(BaseModel):
    n_attacks: int


class MetricsConfig(BaseModel):
    uniqueness: bool


class RiskAssessmentConfig(BaseModel):
    data_format: str
    targets: List[str] = Field(default_factory=list)
    n_random_targets: int
    n_outlier_targets: int
    n_iterations: int
    columns_excluded: List[str]
    linkage: Optional[LinkageConfig] = None
    singlingout_uni: Optional[SinglingOutConfig] = None
    singlingout_multi: Optional[SinglingOutConfig] = None
    attribute_inference: Optional[AttributeInferenceConfig] = None
    metrics: Optional[MetricsConfig] = None
