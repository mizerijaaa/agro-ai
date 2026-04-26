from __future__ import annotations

import argparse
import json
from pathlib import Path

import numpy as np
import pandas as pd
from sklearn.metrics import mean_absolute_error
from sklearn.model_selection import GroupKFold
from xgboost import XGBRegressor


def select_yield_column(df: pd.DataFrame) -> str:
    # We try common label column names produced by our pipeline.
    for c in ["yield_value", "Yield"]:
        if c in df.columns:
            return c
    raise ValueError("No yield label column found (expected yield_value)")


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--table", required=True, help="training_table.parquet")
    ap.add_argument("--out", required=True, help="Output directory for model artifacts")
    ap.add_argument("--min_rows_per_crop", type=int, default=10)
    args = ap.parse_args()

    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)

    df = pd.read_parquet(args.table)
    ycol = select_yield_column(df)

    # Basic cleaning: keep rows with yield label.
    df = df[df[ycol].notna()].copy()

    # Feature set (baseline): numeric climate aggregates + lat/lon if present + FAO distance features.
    drop_cols = {"country", "year", "crop_id", ycol, "production_value", "area_harvested_value"}
    feat_cols = [c for c in df.columns if c not in drop_cols and pd.api.types.is_numeric_dtype(df[c])]
    if not feat_cols:
        raise SystemExit("No numeric feature columns found. Provide climate aggregates and/or FAO requirement features.")

    models = {}
    metrics = {}

    for crop_id, d in df.groupby("crop_id"):
        if len(d) < args.min_rows_per_crop:
            continue
        X = d[feat_cols].fillna(0.0).to_numpy()
        y = d[ycol].to_numpy()

        # Time-aware split proxy: group by year.
        gkf = GroupKFold(n_splits=min(5, len(np.unique(d["year"])) if "year" in d.columns else 5))
        maes = []
        for train_idx, test_idx in gkf.split(X, y, groups=d["year"] if "year" in d.columns else None):
            m = XGBRegressor(
                n_estimators=400,
                max_depth=5,
                learning_rate=0.05,
                subsample=0.9,
                colsample_bytree=0.9,
                reg_lambda=1.0,
                random_state=42,
            )
            m.fit(X[train_idx], y[train_idx])
            pred = m.predict(X[test_idx])
            maes.append(float(mean_absolute_error(y[test_idx], pred)))

        final = XGBRegressor(
            n_estimators=600,
            max_depth=5,
            learning_rate=0.05,
            subsample=0.9,
            colsample_bytree=0.9,
            reg_lambda=1.0,
            random_state=42,
        )
        final.fit(X, y)
        models[crop_id] = final
        metrics[crop_id] = {"mae": float(np.mean(maes)), "rows": int(len(d))}

    if not models:
        raise SystemExit("No crop models trained. Check min_rows_per_crop and training data coverage.")

    # Save artifacts (xgboost json + metadata)
    model_dir = out / "models"
    model_dir.mkdir(parents=True, exist_ok=True)
    for crop_id, m in models.items():
        m.save_model(str(model_dir / f"{crop_id}.xgb.json"))

    meta = {
        "feature_columns": feat_cols,
        "label_column": ycol,
        "metrics": metrics,
    }
    (out / "metadata.json").write_text(json.dumps(meta, indent=2), encoding="utf-8")
    print(f"Wrote models to {model_dir}")
    print(f"Wrote metadata to {out / 'metadata.json'}")


if __name__ == "__main__":
    main()

