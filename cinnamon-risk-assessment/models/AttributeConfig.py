from pydantic import BaseModel, Field
from typing import List, Optional, Union


# Define configuration sub-models

class DateFormatConfiguration(BaseModel):
    name: str = Field("DateFormatConfiguration", Literal=True)
    dateFormatter: Optional[str] = None


class StringPatternConfiguration(BaseModel):
    name: str = Field("StringPatternConfiguration", Literal=True)
    pattern: Optional[str] = None


class RangeConfiguration(BaseModel):
    name: str = Field("RangeConfiguration", Literal=True)
    minValue: Optional[int] = None
    maxValue: Optional[int] = None


# Union type to allow different configuration types
ConfigurationType = Union[DateFormatConfiguration, StringPatternConfiguration, RangeConfiguration]


# Main attribute configuration model
class AttributeConfiguration(BaseModel):
    index: int
    name: str
    type: str
    scale: str
    configurations: Optional[List[ConfigurationType]] = Field(default_factory=list)


# Model to encapsulate all configurations
class AttributeConfigList(BaseModel):
    configurations: List[AttributeConfiguration]
