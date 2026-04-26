from __future__ import annotations

import argparse
import datetime as dt
import json
from pathlib import Path

import pandas as pd

from ml.openmeteo import fetch_daily_archive


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--parcels_csv", required=True, help="CSV with columns: parcel_id, latitude, longitude")
    ap.add_argument("--start", required=True, help="Start date yyyy-mm-dd")
    ap.add_argument("--end", required=True, help="End date yyyy-mm-dd")
    ap.add_argument("--out", required=True, help="Output folder")
    args = ap.parse_args()

    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)

    start = dt.date.fromisoformat(args.start)
    end = dt.date.fromisoformat(args.end)

    parcels = pd.read_csv(args.parcels_csv)
    for _, row in parcels.iterrows():
        pid = str(row["parcel_id"])
        lat = float(row["latitude"])
        lon = float(row["longitude"])
        series = fetch_daily_archive(lat, lon, start, end)
        (out / f"parcel_{pid}_daily.json").write_text(
            json.dumps(series.__dict__, ensure_ascii=False),
            encoding="utf-8",
        )
        print(f"Wrote parcel_{pid}_daily.json")


if __name__ == "__main__":
    main()

