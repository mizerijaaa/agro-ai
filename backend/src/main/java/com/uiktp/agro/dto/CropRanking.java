package com.uiktp.agro.dto;

/**
 * Топ култури за извештај: погодност (0–100), принос, ризик по ставка.
 */
public record CropRanking(
        int rank,
        String cropName,
        int suitabilityPercent,
        double expectedYieldTonPerHa,
        String riskLevel
) {}
