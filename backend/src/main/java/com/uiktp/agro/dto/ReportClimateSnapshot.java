package com.uiktp.agro.dto;

public record ReportClimateSnapshot(
        double temperatureC,
        double precipitationMmPerDay,
        double precipitation7dMm,
        double evapotranspirationMmPerDay,
        double soilMoisture,
        String soilMoistureLabel
) {}
