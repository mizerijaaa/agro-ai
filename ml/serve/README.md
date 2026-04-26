## Inference service (FastAPI)

This service loads model artifacts produced by `ml/train.py` and exposes a simple HTTP API used by the Spring Boot backend.

### Run locally

```bash
python -m venv .venv && source .venv/bin/activate
pip install -r ml/requirements.txt

export AGRO_AI_ML_ARTIFACT_DIR="ml_artifacts"
export AGRO_AI_ML_VERSION="v1"
export AGRO_AI_CROP_REQ_PATH="ml/data/crop_requirements/crop_requirements_v1.json"

uvicorn ml.serve.app:app --host 0.0.0.0 --port 8090
```

### Endpoints
- `GET /health`
- `POST /recommend`

