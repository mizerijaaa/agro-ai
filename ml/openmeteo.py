from __future__ import annotations

import datetime as dt
from dataclasses import dataclass

import requests


ARCHIVE_ENDPOINT = "https://archive-api.open-meteo.com/v1/archive"


@dataclass(frozen=True)
class DailyClimateSeries:
    dates: list[str]
    temperature_2m_mean: list[float] | None
    temperature_2m_min: list[float] | None
    temperature_2m_max: list[float] | None
    precipitation_sum: list[float] | None
    et0_fao_evapotranspiration: list[float] | None
    soil_moisture_0_to_7cm: list[float] | None


def fetch_daily_archive(
    lat: float,
    lon: float,
    start_date: dt.date,
    end_date: dt.date,
    timezone: str = "Europe/Skopje",
) -> DailyClimateSeries:
    daily_vars = [
        "temperature_2m_mean",
        "temperature_2m_min",
        "temperature_2m_max",
        "precipitation_sum",
        "et0_fao_evapotranspiration",
    ]
    # Soil moisture is only available as hourly in many models; archive supports some soil moisture depths as hourly.
    # We request hourly soil moisture and later aggregate to daily mean in feature pipeline when needed.
    hourly_vars = [
        "soil_moisture_0_to_7cm",
    ]

    params = {
        "latitude": lat,
        "longitude": lon,
        "start_date": start_date.isoformat(),
        "end_date": end_date.isoformat(),
        "daily": ",".join(daily_vars),
        "hourly": ",".join(hourly_vars),
        "timezone": timezone,
    }
    res = requests.get(ARCHIVE_ENDPOINT, params=params, timeout=120)
    res.raise_for_status()
    data = res.json()

    daily = data.get("daily") or {}
    return DailyClimateSeries(
        dates=list(daily.get("time") or []),
        temperature_2m_mean=daily.get("temperature_2m_mean"),
        temperature_2m_min=daily.get("temperature_2m_min"),
        temperature_2m_max=daily.get("temperature_2m_max"),
        precipitation_sum=daily.get("precipitation_sum"),
        et0_fao_evapotranspiration=daily.get("et0_fao_evapotranspiration"),
        soil_moisture_0_to_7cm=None,
    )

