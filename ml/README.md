## Agro AI — Data-driven ML module

This folder contains the **training pipeline** and an optional **inference service** for a data-driven crop recommendation model.

### Data sources
- **FAOSTAT QCL** (Production: Crops and livestock products) bulk downloads (normalized).
- **Open-Meteo** historical + forecast climate (queried by lat/lon).
- **FAO crop requirement datasets** (provided as a machine-readable file in `ml/data/crop_requirements/`).

### Quickstart (training)
1. Create a virtualenv and install dependencies:

```bash
python -m venv .venv && source .venv/bin/activate
pip install -r ml/requirements.txt
```

2. Download FAOSTAT QCL snapshot (normalized bulk file):

```bash
python -m ml.scripts.download_faostat_qcl --out ml/data_raw
```

3. Build a country/year/crop training table (labels only, first step):

```bash
python -m ml.scripts.build_faostat_labels --in ml/data_raw --out ml/data_interim
```

Further steps (Open-Meteo historical fetch, feature building, model training, serving) are added in subsequent scripts.

