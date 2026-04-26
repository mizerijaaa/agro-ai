from __future__ import annotations

import json
from pathlib import Path

import numpy as np
import shap
from xgboost import XGBRegressor


def load_model(path: Path) -> XGBRegressor:
    m = XGBRegressor()
    m.load_model(str(path))
    return m


def shap_explain_single(model: XGBRegressor, feature_names: list[str], x: np.ndarray) -> list[dict]:
    explainer = shap.TreeExplainer(model)
    vals = explainer.shap_values(x.reshape(1, -1))
    vals = vals[0]
    pairs = list(zip(feature_names, vals))
    pairs.sort(key=lambda p: abs(p[1]), reverse=True)
    return [{"feature": f, "shap": float(v)} for f, v in pairs[:10]]

