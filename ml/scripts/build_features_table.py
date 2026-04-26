from __future__ import annotations

import argparse
import json
from pathlib import Path

import pandas as pd

from ml.features import aggregate_climate_daily_to_country_year, build_country_year_features
from ml.scripts.load_crop_requirements import load_requirements


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--labels", required=True, help="Path to faostat_labels.parquet")
    ap.add_argument("--climate_daily", required=False, help="Optional daily climate parquet/csv (country,date,tmean_c,tmin_c,tmax_c,precip_mm,et0_mm)")
    ap.add_argument("--crop_requirements", required=False, help="Optional crop_requirements_v1.json")
    ap.add_argument("--out", required=True, help="Output folder")
    args = ap.parse_args()

    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)

    labels = pd.read_parquet(args.labels)

    if args.climate_daily:
        if args.climate_daily.endswith(".parquet"):
            daily = pd.read_parquet(args.climate_daily)
        else:
            daily = pd.read_csv(args.climate_daily)
        climate = aggregate_climate_daily_to_country_year(daily)
    else:
        # Allow running label-only build to validate pipeline.
        climate = pd.DataFrame(columns=["country", "year"])

    reqs = load_requirements(Path(args.crop_requirements)) if args.crop_requirements else None

    feat = build_country_year_features(labels=labels, climate_country_year=climate, crop_requirements=reqs)
    out_path = out / "training_table.parquet"
    feat.to_parquet(out_path, index=False)
    print(f"Wrote: {out_path}")


if __name__ == "__main__":
    main()

