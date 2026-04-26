from __future__ import annotations

import argparse
import io
import os
import zipfile
from pathlib import Path

import requests
from tqdm import tqdm


FAOSTAT_BULK_BASE = "https://fenixservices.fao.org/faostat/static/bulkdownloads"
# FAOSTAT QCL (Production: Crops and livestock products) — normalized long format.
QCL_NORMALIZED_ZIP = "Production_Crops_Livestock_E_All_Data_(Normalized).zip"


def download_file(url: str, out_path: Path) -> None:
    out_path.parent.mkdir(parents=True, exist_ok=True)
    with requests.get(url, stream=True, timeout=120) as r:
        r.raise_for_status()
        total = int(r.headers.get("Content-Length", "0") or "0")
        with open(out_path, "wb") as f:
            pbar = tqdm(total=total, unit="B", unit_scale=True, desc=out_path.name)
            for chunk in r.iter_content(chunk_size=1024 * 1024):
                if not chunk:
                    continue
                f.write(chunk)
                pbar.update(len(chunk))
            pbar.close()


def extract_main_csv(zip_path: Path, out_dir: Path) -> Path:
    out_dir.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(zip_path, "r") as z:
        # The main CSV is usually named like the zip file (without .zip)
        expected_prefix = zip_path.stem
        csv_names = [n for n in z.namelist() if n.lower().endswith(".csv")]
        if not csv_names:
            raise RuntimeError("No CSV found inside the FAOSTAT bulk zip")
        # Prefer exact stem match, otherwise first csv.
        main_name = None
        for n in csv_names:
            if Path(n).stem == expected_prefix:
                main_name = n
                break
        main_name = main_name or csv_names[0]
        out_csv = out_dir / Path(main_name).name
        with z.open(main_name) as src, open(out_csv, "wb") as dst:
            dst.write(src.read())
        return out_csv


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True, help="Output directory for raw data (zip + extracted csv)")
    ap.add_argument("--url", default=f"{FAOSTAT_BULK_BASE}/{QCL_NORMALIZED_ZIP}", help="Override bulk download URL")
    args = ap.parse_args()

    out_dir = Path(args.out)
    out_dir.mkdir(parents=True, exist_ok=True)
    zip_path = out_dir / QCL_NORMALIZED_ZIP
    csv_dir = out_dir / "extracted"

    print(f"Downloading: {args.url}")
    download_file(args.url, zip_path)

    print("Extracting main CSV...")
    csv_path = extract_main_csv(zip_path, csv_dir)
    print(f"Extracted: {csv_path}")

    print("Done.")


if __name__ == "__main__":
    main()

