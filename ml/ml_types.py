from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Literal


CropId = Literal["wheat", "maize", "sunflower", "barley", "tomato", "pepper_green"]


@dataclass(frozen=True)
class TemperatureRange:
    min: float
    max: float


@dataclass(frozen=True)
class CropRequirements:
    crop_id: CropId
    display_name: str
    optimal_temperature_c: TemperatureRange
    absolute_temperature_c: TemperatureRange | None
    seasonal_water_need_mm: float | None
    drought_sensitivity: str | None
    notes_source: str
    extra: dict[str, Any]

