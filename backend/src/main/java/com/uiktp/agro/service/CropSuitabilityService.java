package com.uiktp.agro.service;

import com.uiktp.agro.dto.CropRanking;
import com.uiktp.agro.model.ClimateData;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Табела: култура, типови на почва, температура, врнежи, влажност.
 * topRankings() дава 3 култури со погодност %, принос т/ха и ризик по ставка.
 */
@Component
public class CropSuitabilityService {

    public record Row(
            String cropName,
            int tempMinC,
            int tempMaxC,
            int rainMinT,
            int rainMaxT,
            int waterNeed,
            String[] soilKeywords) {
    }

    private record Scored(Row row, double score) {
    }

    private static final List<Row> TABLE = new ArrayList<>();
    private static final Map<String, Double> TYPICAL_YIELD = new HashMap<>();

    static {
        r("Пченица", 10, 25, 30, 80, 2, "глин", "илов");
        r("Пченка", 18, 30, 50, 120, 3, "глин", "илов");
        r("Јачмен", 8, 20, 20, 60, 1, "песок", "песо", "илов");
        r("Сончоглед", 20, 30, 20, 50, 1, "песок", "песо");
        r("Компир", 10, 20, 50, 100, 2, "илов");
        r("Домати", 18, 30, 40, 80, 2, "илов");
        r("Пипер", 20, 30, 40, 70, 2, "илов");
        r("Краставица", 18, 28, 50, 90, 3, "илов");
        r("Лук", 10, 20, 20, 50, 1, "песок", "песо");
        r("Кромид", 12, 25, 20, 60, 1, "песок", "песо");
        r("Морков", 10, 20, 30, 70, 2, "песок", "песо");
        r("Зелка", 10, 20, 50, 100, 3, "глин");
        r("Спанаќ", 5, 15, 30, 60, 2, "илов");
        r("Грав", 18, 28, 40, 80, 2, "илов");
        r("Грашок", 10, 20, 40, 70, 2, "илов");
        r("Детелина", 10, 25, 50, 100, 3, "глин");
        r("Луцерка", 15, 30, 40, 90, 2, "глин");
        r("Овес", 10, 20, 30, 80, 2, "песок", "песо");
        r("Рж", 5, 15, 20, 60, 1, "песок", "песо");
        r("Тутун", 20, 30, 30, 70, 1, "песок", "песо");
        r("Јаболко", 10, 25, 50, 100, 2, "илов");
        r("Круша", 10, 25, 50, 100, 2, "илов");
        r("Слива", 10, 25, 40, 80, 2, "глин");
        r("Праска", 15, 30, 40, 70, 2, "песок", "песо");
        r("Цреша", 10, 25, 40, 80, 2, "илов");
        r("Вишна", 10, 25, 40, 80, 2, "глин");
        r("Лозје", 15, 30, 20, 60, 1, "песок", "песо");
        r("Пченка за силажа", 20, 30, 60, 120, 3, "глин");
        r("Ориз", 20, 35, 100, 200, 4, "глин", "мочур");
        r("Кикирики", 20, 30, 30, 60, 1, "песок", "песо");

        for (Row row : TABLE) {
            TYPICAL_YIELD.putIfAbsent(row.cropName, defaultYieldForName(row.cropName));
        }
    }

    private static double defaultYieldForName(String name) {
        return switch (name) {
            case "Пченица" -> 4.5;
            case "Пченка", "Пченка за силажа" -> 8.0;
            case "Јачмен" -> 3.6;
            case "Сончоглед" -> 2.8;
            case "Компир" -> 25.0;
            case "Домати" -> 45.0;
            case "Пипер" -> 12.0;
            case "Краставица" -> 35.0;
            case "Лук" -> 25.0;
            case "Кромид" -> 35.0;
            case "Морков" -> 40.0;
            case "Зелка" -> 45.0;
            case "Спанаќ" -> 12.0;
            case "Грав" -> 2.2;
            case "Грашок" -> 2.0;
            case "Детелина" -> 8.0;
            case "Луцерка" -> 12.0;
            case "Овес" -> 3.2;
            case "Рж" -> 2.8;
            case "Тутун" -> 2.0;
            case "Јаболко" -> 35.0;
            case "Круша" -> 30.0;
            case "Слива" -> 12.0;
            case "Праска" -> 18.0;
            case "Цреша" -> 10.0;
            case "Вишна" -> 8.0;
            case "Лозје" -> 8.0;
            case "Ориз" -> 5.0;
            case "Кикирики" -> 2.5;
            default -> 3.5;
        };
    }

    private static void r(
            String name, int t0, int t1, int pr0, int pr1, int w, String... k) {
        TABLE.add(new Row(name, t0, t1, pr0, pr1, w, k));
    }

    public List<CropRanking> topRankings(ClimateData d, String soilType, String globalRiskLevel, int limit) {
        double air = d.getTemperature();
        double precip7 = d.getPrecipitation() * 7.0;
        double moist = d.getSoilMoisture();
        int obsWater = waterCategory(moist);
        String soil = normalizeSoil(soilType);

        List<Scored> all = new ArrayList<>();
        for (Row row : TABLE) {
            all.add(new Scored(row, score(row, soil, air, precip7, obsWater)));
        }
        all.sort(Comparator.comparingDouble(Scored::score).reversed());
        int n = Math.min(limit, all.size());
        if (n == 0) {
            return List.of();
        }
        double maxS = all.get(0).score();
        double minS = all.get(n - 1).score();
        double range = maxS - minS;
        if (range < 0.01) {
            range = 1.0;
        }
        double yieldMult = switch (globalRiskLevel) {
            case "HIGH" -> 0.52;
            case "MEDIUM" -> 0.75;
            default -> 1.0;
        };

        List<CropRanking> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Scored sc = all.get(i);
            int rank = i + 1;
            int pct = (int) Math.round(72 + 26 * (sc.score() - minS) / range);
            pct = Math.min(98, Math.max(68, pct));
            String crop = sc.row().cropName;
            double baseY = TYPICAL_YIELD.getOrDefault(crop, 3.5);
            double suitFactor = 0.82 + 0.18 * (pct / 100.0);
            double y = round2(baseY * yieldMult * suitFactor);
            String rsk = perCropRisk(globalRiskLevel, sc.row(), obsWater, pct, rank);
            out.add(new CropRanking(rank, crop, pct, y, rsk));
        }
        return out;
    }

    private static String perCropRisk(String global, Row row, int obsWater, int suitabilityPercent, int rank) {
        if ("HIGH".equals(global)) {
            return "HIGH";
        }
        if ("MEDIUM".equals(global) && (rank > 1 || suitabilityPercent < 80)) {
            return "MEDIUM";
        }
        int gap = Math.abs(obsWater - row.waterNeed);
        if (gap >= 2) {
            return "MEDIUM";
        }
        if (suitabilityPercent < 78) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private static int waterCategory(double m) {
        if (m < 0.12) {
            return 1;
        }
        if (m < 0.20) {
            return 1;
        }
        if (m < 0.32) {
            return 2;
        }
        if (m < 0.40) {
            return 3;
        }
        return 4;
    }

    private static String normalizeSoil(String soilType) {
        if (soilType == null) {
            return "";
        }
        return soilType.toLowerCase(Locale.ROOT)
                .replace("љ", "л")
                .replace("њ", "н");
    }

    private static double score(Row row, String soil, double airC, double precip7, int obsWater) {
        boolean soilOk = soilMatches(row, soil);
        boolean tempOk = airC + 0.5 >= row.tempMinC && airC - 0.5 <= row.tempMaxC;
        double tBonus = 0;
        if (!tempOk) {
            double tMid = (row.tempMinC + row.tempMaxC) / 2.0;
            tBonus = -0.1 * Math.abs(airC - tMid);
        }
        double rMid = (row.rainMinT + row.rainMaxT) / 2.0;
        double wScale = 0.45;
        double pFit = 1.0 - Math.min(1.0, Math.abs(precip7 - rMid * wScale) / (rMid * wScale + 1));
        if (Double.isNaN(pFit)) {
            pFit = 0;
        }
        int wNeed = row.waterNeed;
        int wDiff = Math.abs(obsWater - wNeed);
        double moFit = 1.0 - wDiff * 0.2;

        double s = 0;
        s += soilOk ? 3.0 : 0.2;
        s += tempOk ? 2.0 : 0.5;
        s += tBonus;
        s += 1.1 * pFit;
        s += 1.3 * Math.max(0, moFit);
        return s;
    }

    private static boolean soilMatches(Row row, String soil) {
        if (soil == null || soil.isBlank()) {
            return true;
        }
        for (String k : row.soilKeywords) {
            if (soil.contains(k)) {
                return true;
            }
        }
        return false;
    }

    private static double round2(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
