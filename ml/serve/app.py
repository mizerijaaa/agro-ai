from __future__ import annotations

import json
import logging
import os
from pathlib import Path
from typing import Any

import numpy as np
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from xgboost import XGBRegressor

from ml.explain import shap_explain_single
from ml.scripts.load_crop_requirements import load_requirements


class ParcelContext(BaseModel):
    latitude: float
    longitude: float
    soilType: str | None = None


class ClimateContext(BaseModel):
    # Minimal baseline features; the backend will send aggregates it has or computed.
    tmean_c_year: float | None = None
    tmin_c_year: float | None = None
    tmax_c_year: float | None = None
    precip_mm_year: float | None = None
    et0_mm_year: float | None = None
    water_balance_mm_year: float | None = None


class RecommendRequest(BaseModel):
    parcel: ParcelContext
    climate: ClimateContext | None = None
    candidateCrops: list[str] = Field(default_factory=list)


class CropRecommendation(BaseModel):
    cropId: str
    expectedYieldTonPerHa: float
    suitabilityScore: float
    topFactors: list[dict[str, Any]]
    faoOptimalTemperatureC: dict[str, float] | None = None


class RecommendResponse(BaseModel):
    rankedCrops: list[CropRecommendation]
    modelVersion: str


def load_models(model_dir: Path) -> dict[str, XGBRegressor]:
    models: dict[str, XGBRegressor] = {}
    for p in model_dir.glob("*.xgb.json"):
        cid = p.stem.replace(".xgb", "")
        m = XGBRegressor()
        m.load_model(str(p))
        models[cid] = m
    return models


ARTIFACT_DIR = Path(os.environ.get("AGRO_AI_ML_ARTIFACT_DIR", "ml_artifacts")).resolve()
META = json.loads((ARTIFACT_DIR / "metadata.json").read_text(encoding="utf-8"))
FEATURE_COLUMNS: list[str] = META["feature_columns"]
MODEL_VERSION = os.environ.get("AGRO_AI_ML_VERSION", "v1")
MODELS = load_models(ARTIFACT_DIR / "models")

REQ_PATH = os.environ.get("AGRO_AI_CROP_REQ_PATH")
REQS = load_requirements(Path(REQ_PATH)) if REQ_PATH else {}


log = logging.getLogger("agro_ai_ml")
logging.basicConfig(level=os.environ.get("AGRO_AI_ML_LOG_LEVEL", "INFO"))

app = FastAPI(title="Agro AI ML Inference", version=MODEL_VERSION)


@app.get("/health")
def health() -> dict:
    return {"ok": True, "models": sorted(MODELS.keys()), "version": MODEL_VERSION}


@app.post("/recommend", response_model=RecommendResponse)
def recommend(req: RecommendRequest) -> RecommendResponse:
    climate = req.climate or ClimateContext()
    base = {
        "tmean_c_year": climate.tmean_c_year,
        "tmin_c_year": climate.tmin_c_year,
        "tmax_c_year": climate.tmax_c_year,
        "precip_mm_year": climate.precip_mm_year,
        "et0_mm_year": climate.et0_mm_year,
        "water_balance_mm_year": climate.water_balance_mm_year,
    }

    results: list[CropRecommendation] = []
    candidates = req.candidateCrops or list(MODELS.keys())
    raw_preds: list[tuple[str, float]] = []
    for crop_id in candidates:
        model = MODELS.get(crop_id)
        if model is None:
            continue
        # Add FAO requirement features if present
        row = dict(base)
        r = REQS.get(crop_id)
        if r:
            row["fao_opt_temp_min_c"] = r.optimal_temperature_c.min
            row["fao_opt_temp_max_c"] = r.optimal_temperature_c.max
            if row.get("tmean_c_year") is not None:
                row["temp_below_opt_c"] = max(0.0, row["fao_opt_temp_min_c"] - row["tmean_c_year"])
                row["temp_above_opt_c"] = max(0.0, row["tmean_c_year"] - row["fao_opt_temp_max_c"])

        x = np.array([float(row.get(c, 0.0) or 0.0) for c in FEATURE_COLUMNS], dtype=float)
        yhat = float(model.predict(x.reshape(1, -1))[0])
        raw_preds.append((crop_id, yhat))
        # Convert kg/ha -> t/ha for API consistency (backend/UI expect ton/ha).
        yhat_t_ha = yhat / 1000.0
        top = shap_explain_single(model, FEATURE_COLUMNS, x)
        results.append(
            CropRecommendation(
                cropId=crop_id,
                expectedYieldTonPerHa=yhat_t_ha,
                suitabilityScore=0.0,  # filled after min-max scaling across candidates
                topFactors=top,
                faoOptimalTemperatureC={"min": r.optimal_temperature_c.min, "max": r.optimal_temperature_c.max} if r else None,
            )
        )

    if not results:
        raise HTTPException(status_code=400, detail="No candidates matched available models")

    # Suitability score: min-max normalize predicted yields across candidate crops (0..100).
    ys = [r.expectedYieldTonPerHa for r in results]
    y_min = float(min(ys))
    y_max = float(max(ys))
    denom = (y_max - y_min) if (y_max - y_min) > 1e-9 else 1.0
    for r in results:
        r.suitabilityScore = float(np.clip(100.0 * ((r.expectedYieldTonPerHa - y_min) / denom), 0.0, 100.0))

    # Temporary debug logging (can be disabled by setting AGRO_AI_ML_LOG_LEVEL=WARNING)
    try:
        log.info("recommend candidates=%s", candidates)
        log.info("feature_columns=%s", FEATURE_COLUMNS)
        log.info("raw_preds_kg_ha=%s", raw_preds)
        log.info("y_range_t_ha=(%s,%s)", y_min, y_max)
    except Exception:
        pass

    results.sort(key=lambda rr: rr.expectedYieldTonPerHa, reverse=True)
    return RecommendResponse(rankedCrops=results, modelVersion=MODEL_VERSION)

