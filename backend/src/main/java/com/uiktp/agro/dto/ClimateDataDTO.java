package com.uiktp.agro.dto;

/**
 * Open-Meteo вредности: просеци за 7-дневната прогноза за даден lat/lon.
 */
public record ClimateDataDTO(
        double averageTemperatureCelsius,
        double averageDailyPrecipitationMm,
        double averageEt0MmPerDay,
        double averageSoilMoisture,
        double averageSoilTemperature0cmCelsius,
        String dataSource
) {
    public static ClimateDataDTO empty(String reason) {
        return new ClimateDataDTO(0, 0, 0, 0, 0, reason != null ? reason : "empty");
    }
}
