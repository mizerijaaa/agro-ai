package com.uiktp.agro.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uiktp.agro.dto.CropRanking;
import com.uiktp.agro.model.ClimateData;
import com.uiktp.agro.model.Parcel;
import com.uiktp.agro.model.Recommendation;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class RecommendationService {
    private final ClimateService climateService;
    private final CropSuitabilityService cropSuitabilityService;
    private final ObjectMapper objectMapper;
    private final MlInferenceClient mlInferenceClient;

    public RecommendationService(
            ClimateService climateService,
            CropSuitabilityService cropSuitabilityService,
            ObjectMapper objectMapper,
            MlInferenceClient mlInferenceClient) {
        this.climateService = climateService;
        this.cropSuitabilityService = cropSuitabilityService;
        this.objectMapper = objectMapper;
        this.mlInferenceClient = mlInferenceClient;
    }

    public Recommendation generate(Parcel parcel) {
        ClimateData data = climateService.fetchForecastForParcel(parcel);
        Recommendation rec = new Recommendation();
        rec.setParcel(parcel);

        String risk;
        String soilStatus;
        String irrigationAdvice;
        double balance = data.getPrecipitation() - data.getEvapotranspiration();

        if (data.getSoilMoisture() < 0.10) {
            soilStatus = "Критично суво";
            irrigationAdvice = "Суша ризик: ВИСОК - препорачано итно наводнување";
        } else if (data.getSoilMoisture() < 0.20) {
            soilStatus = "Суво";
            irrigationAdvice = "Следи ја состојбата и разгледај умерено наводнување";
        } else if (data.getSoilMoisture() <= 0.35) {
            soilStatus = "Оптимално";
            irrigationAdvice = "Нормална состојба, редовно следење";
        } else if (data.getSoilMoisture() <= 0.45) {
            soilStatus = "Влажно";
            irrigationAdvice = "Одложи дополнително наводнување и обработка";
        } else {
            soilStatus = "Презаситено";
            irrigationAdvice = "Ризик од болести и анаеробни услови, избегнувај наводнување";
        }

        if (data.getSoilMoisture() < 0.10 || balance < -2.0) {
            risk = "HIGH";
        } else if (data.getSoilMoisture() < 0.20 || balance < 0) {
            risk = "MEDIUM";
        } else {
            risk = "LOW";
        }

        // Data-driven ML inference (no rule-based crop scoring). Candidate crops for v1.
        List<String> candidates = List.of("wheat", "maize", "sunflower", "barley", "tomato", "pepper_green");
        try {
            var ml = mlInferenceClient.recommend(parcel, data, candidates);
            rec.setMlOutputJson(objectMapper.writeValueAsString(ml));
            var ranked = ml.get("rankedCrops");
            if (ranked != null && ranked.isArray() && ranked.size() > 0) {
                var first = ranked.get(0);
                String cropId = first.path("cropId").asText("wheat");
                double y = first.path("expectedYieldTonPerHa").asDouble(0.0);
                rec.setSuggestedCrop(cropId);
                rec.setExpectedYieldTonPerHa(y);
                // Also populate cropRankingsJson for existing UI by mapping to CropRanking-like shape.
                List<CropRanking> mapped = new ArrayList<>();
                int rank = 1;
                for (var node : ranked) {
                    mapped.add(new CropRanking(
                            rank++,
                            node.path("cropId").asText(),
                            (int) Math.round(node.path("suitabilityScore").asDouble(0.0)),
                            node.path("expectedYieldTonPerHa").asDouble(0.0),
                            risk
                    ));
                    if (rank > 6) break;
                }
                rec.setCropRankingsJson(objectMapper.writeValueAsString(mapped));
            } else {
                rec.setSuggestedCrop("wheat");
                rec.setExpectedYieldTonPerHa(0.0);
            }
        } catch (Exception e) {
            // ML is required for correct output. Bubble up a clear error so we don't silently fall back to 0.0.
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_GATEWAY,
                    "ML service unavailable or failed. Start the ML inference service and ensure agro.ml.base-url is reachable.",
                    e
            );
        }
        rec.setRiskLevel(risk);
        rec.setSoilMoistureStatus(soilStatus);
        rec.setIrrigationAdvice(irrigationAdvice);
        rec.setExplanation("Препораката е генерирана од ML модел обучен на FAOSTAT (принос/производство) и климатски податоци (Open‑Meteo), "
                + "со споредба со FAO барања по култури. Ако сервисот за ML не е достапен, прикажана е основна препорака.");
        return rec;
    }

    public List<CropRanking> parseRankingsJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (Exception e) {
            return List.of();
        }
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
