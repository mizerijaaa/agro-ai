## FAO crop requirements (input data)

Place crop requirement datasets (FAO) here in **JSON** format using the schema below.

This project intentionally does **not** hardcode agronomic thresholds (no synthetic assumptions). Instead, requirements are loaded from a dataset file you provide.

### Expected file
- `ml/data/crop_requirements/crop_requirements_v1.json`

### Schema (per crop)
Each crop entry should contain (minimum):
- `crop_id`: string (matches ML crop id: `wheat|maize|sunflower|barley|tomato|pepper_green`)\n+- `display_name`: string\n+- `optimal_temperature_c`: `{ \"min\": number, \"max\": number }`\n+- `absolute_temperature_c`: `{ \"min\": number, \"max\": number }` (optional but recommended)\n+- `seasonal_water_need_mm`: number (optional)\n+- `drought_sensitivity`: one of `low|medium|high` (optional)\n+- `notes_source`: string (citation/URL/title)\n+\nAdditional fields are allowed and will be preserved.\n+
