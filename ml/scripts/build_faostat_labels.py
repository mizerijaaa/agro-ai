from __future__ import annotations

import argparse
from pathlib import Path

import pandas as pd


V1_CROPS = {
    "Wheat": "wheat",
    "Maize (corn)": "maize",
    "Sunflower seed": "sunflower",
    "Barley": "barley",
    "Tomatoes": "tomato",
    "Chillies and peppers, green (Capsicum spp. and Pimenta spp.)": "pepper_green",
}


NEIGHBOR_AREAS = {
    "North Macedonia",
    "Albania",
    "Bulgaria",
    "Greece",
    "Serbia",
    "Croatia",
    "Bosnia and Herzegovina",
    "Montenegro",
    "Romania",
    "Slovenia",
    "Hungary",
}


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--in", dest="in_dir", required=True, help="Input directory with extracted FAOSTAT CSV")
    ap.add_argument("--out", dest="out_dir", required=True, help="Output directory")
    ap.add_argument("--areas", default="mk_only", choices=["mk_only", "neighbors"], help="Geographic scope")
    ap.add_argument("--min_year", type=int, default=2000)
    ap.add_argument("--max_year", type=int, default=2024)
    args = ap.parse_args()

    in_dir = Path(args.in_dir)
    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    # Find the extracted csv (bulk normalized)
    csvs = sorted(in_dir.glob("**/*.csv"))
    if not csvs:
        raise SystemExit(f"No CSV found under {in_dir}")
    csv_path = csvs[0]

    df = pd.read_csv(csv_path)
    # Expected FAOSTAT bulk columns include:
    # Area, Item, Element, Year, Unit, Value, Flag, Note
    needed = {"Area", "Item", "Element", "Year", "Unit", "Value"}
    missing = needed - set(df.columns)
    if missing:
        raise SystemExit(f"Missing expected columns in FAOSTAT file: {sorted(missing)}")

    if args.areas == "mk_only":
        df = df[df["Area"] == "North Macedonia"]
    else:
        df = df[df["Area"].isin(NEIGHBOR_AREAS)]

    df = df[df["Item"].isin(V1_CROPS.keys())]
    df = df[(df["Year"] >= args.min_year) & (df["Year"] <= args.max_year)]

    # Label table: we focus on Yield + its unit; also keep production/harvested area if present.
    # Common element names in QCL: "Yield", "Production", "Area harvested"
    elements_keep = {"Yield", "Production", "Area harvested"}
    df = df[df["Element"].isin(elements_keep)]

    df["crop_id"] = df["Item"].map(V1_CROPS)
    df = df.rename(columns={"Area": "country", "Year": "year", "Value": "value", "Unit": "unit", "Element": "element"})

    # Pivot to wide: yield/production/area_harvested columns.
    pivot = df.pivot_table(
        index=["country", "year", "crop_id"],
        columns="element",
        values="value",
        aggfunc="mean",
    ).reset_index()

    # Keep units separately (FAOSTAT can mix units by element).
    units = df.drop_duplicates(subset=["country", "year", "crop_id", "element"])[
        ["country", "year", "crop_id", "element", "unit"]
    ]
    units_out = units.pivot_table(
        index=["country", "year", "crop_id"],
        columns="element",
        values="unit",
        aggfunc="first",
    ).reset_index()

    out_labels = out_dir / "faostat_labels.parquet"
    out_units = out_dir / "faostat_units.parquet"
    pivot.to_parquet(out_labels, index=False)
    units_out.to_parquet(out_units, index=False)

    print(f"Wrote: {out_labels}")
    print(f"Wrote: {out_units}")


if __name__ == "__main__":
    main()

