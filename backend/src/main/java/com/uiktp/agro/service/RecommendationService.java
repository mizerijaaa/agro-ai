package com.uiktp.agro.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uiktp.agro.dto.CropRanking;
import com.uiktp.agro.model.ClimateData;
import com.uiktp.agro.model.Parcel;
import com.uiktp.agro.model.Recommendation;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RecommendationService {
    private final ClimateService climateService;
    private final CropSuitabilityService cropSuitabilityService;
    private final ObjectMapper objectMapper;

    public RecommendationService(
            ClimateService climateService,
            CropSuitabilityService cropSuitabilityService,
            ObjectMapper objectMapper) {
        this.climateService = climateService;
        this.cropSuitabilityService = cropSuitabilityService;
        this.objectMapper = objectMapper;
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

        List<CropRanking> top = cropSuitabilityService.topRankings(data, parcel.getSoilType(), risk, 3);
        if (top.isEmpty()) {
            rec.setSuggestedCrop("Пченица");
            rec.setExpectedYieldTonPerHa(4.0);
        } else {
            CropRanking first = top.get(0);
            rec.setSuggestedCrop(first.cropName());
            rec.setExpectedYieldTonPerHa(first.expectedYieldTonPerHa());
            try {
                rec.setCropRankingsJson(objectMapper.writeValueAsString(top));
            } catch (Exception e) {
                rec.setCropRankingsJson(null);
            }
        }
        rec.setRiskLevel(risk);
        rec.setSoilMoistureStatus(soilStatus);
        rec.setIrrigationAdvice(irrigationAdvice);
        rec.setExplanation("Препораката е изведена со AI-анализа на погодноста на културите според почвата на парцелата, "
                + "просечната температура (" + round(data.getTemperature()) + "°C), "
                + "очекувани врнежи за 7 дена (" + round(data.getPrecipitation() * 7) + " mm), "
                + "евапотранспирација (" + round(data.getEvapotranspiration()) + " mm/ден) и "
                + "влажност на почвата, со воден биланс приближно " + round(balance) + " mm. "
                + "Почва: " + (parcel.getSoilType() != null ? parcel.getSoilType() : "ненаведена")
                + ". Податоци за времето: Open-Meteo.");
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
