from __future__ import annotations

from dataclasses import asdict
from typing import Any

import numpy as np
import pandas as pd

from ml.ml_types import CropRequirements


def build_country_year_features(
    labels: pd.DataFrame,
    climate_country_year: pd.DataFrame,
    crop_requirements: dict[str, CropRequirements] | None = None,
) -> pd.DataFrame:
    """
    Build a training table keyed by (country, year, crop_id).

    - labels: output of build_faostat_labels (wide with Yield/Production/Area harvested columns if present)
    - climate_country_year: aggregated climate keyed by (country, year) OR (country, year, crop_id) depending on availability
    - crop_requirements: optional FAO requirements; if provided, generates distance-to-optimal features.
    """
    df = labels.copy()

    # Normalize column names from FAOSTAT pivot
    if "Yield" in df.columns:
        df = df.rename(columns={"Yield": "yield_value"})
    if "Production" in df.columns:
        df = df.rename(columns={"Production": "production_value"})
    if "Area harvested" in df.columns:
        df = df.rename(columns={"Area harvested": "area_harvested_value"})

    # Join climate features (country-year)
    df = df.merge(climate_country_year, on=["country", "year"], how="left", suffixes=("", "_clim"))

    # Add FAO requirements derived features
    if crop_requirements:
        opt_min = []
        opt_max = []
        for cid in df["crop_id"].tolist():
            req = crop_requirements.get(cid)
            opt_min.append(req.optimal_temperature_c.min if req else np.nan)
            opt_max.append(req.optimal_temperature_c.max if req else np.nan)
        df["fao_opt_temp_min_c"] = opt_min
        df["fao_opt_temp_max_c"] = opt_max

        # Distance-to-optimal vs mean temperature features
        if "tmean_c_gs" in df.columns:
            df["temp_below_opt_c"] = np.maximum(0.0, df["fao_opt_temp_min_c"] - df["tmean_c_gs"])
            df["temp_above_opt_c"] = np.maximum(0.0, df["tmean_c_gs"] - df["fao_opt_temp_max_c"])

    return df


def aggregate_climate_daily_to_country_year(daily: pd.DataFrame) -> pd.DataFrame:
    """
    Input daily climate rows with columns:
      - country, date, tmean_c, tmin_c, tmax_c, precip_mm, et0_mm

    Output country-year aggregates used for baseline training.
    """
    d = daily.copy()
    d["date"] = pd.to_datetime(d["date"])
    d["year"] = d["date"].dt.year

    agg = d.groupby(["country", "year"], as_index=False).agg(
        tmean_c_year=("tmean_c", "mean"),
        tmin_c_year=("tmin_c", "mean"),
        tmax_c_year=("tmax_c", "mean"),
        precip_mm_year=("precip_mm", "sum"),
        et0_mm_year=("et0_mm", "sum"),
    )
    # Water balance proxy
    agg["water_balance_mm_year"] = agg["precip_mm_year"] - agg["et0_mm_year"]
    return agg

