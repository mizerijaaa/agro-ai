from __future__ import annotations

import argparse
import json
from pathlib import Path

from ml.ml_types import CropRequirements, TemperatureRange


def load_requirements(path: Path) -> dict[str, CropRequirements]:
    raw = json.loads(path.read_text(encoding="utf-8"))
    crops = raw.get("crops") or []
    out: dict[str, CropRequirements] = {}
    for c in crops:
        crop_id = c["crop_id"]
        extra = dict(c)
        extra.pop("crop_id", None)
        extra.pop("display_name", None)
        extra.pop("optimal_temperature_c", None)
        extra.pop("absolute_temperature_c", None)
        extra.pop("seasonal_water_need_mm", None)
        extra.pop("drought_sensitivity", None)
        extra.pop("notes_source", None)

        opt = c["optimal_temperature_c"]
        absr = c.get("absolute_temperature_c")
        out[crop_id] = CropRequirements(
            crop_id=crop_id,
            display_name=c["display_name"],
            optimal_temperature_c=TemperatureRange(min=float(opt["min"]), max=float(opt["max"])),
            absolute_temperature_c=TemperatureRange(min=float(absr["min"]), max=float(absr["max"])) if absr else None,
            seasonal_water_need_mm=float(c["seasonal_water_need_mm"]) if c.get("seasonal_water_need_mm") is not None else None,
            drought_sensitivity=c.get("drought_sensitivity"),
            notes_source=c["notes_source"],
            extra=extra,
        )
    return out


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--path", required=True, help="Path to crop_requirements_v1.json")
    args = ap.parse_args()
    m = load_requirements(Path(args.path))
    for k, v in m.items():
        print(k, v.display_name, v.optimal_temperature_c)


if __name__ == "__main__":
    main()

