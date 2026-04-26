package com.uiktp.agro.api;

import com.uiktp.agro.model.AppUser;
import com.uiktp.agro.model.ClimateData;
import com.uiktp.agro.model.Parcel;
import com.uiktp.agro.model.Recommendation;
import com.uiktp.agro.repo.ParcelRepository;
import com.uiktp.agro.repo.RecommendationRepository;
import com.uiktp.agro.service.ClimateService;
import com.uiktp.agro.service.CurrentUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private static final Logger log = LoggerFactory.getLogger(DashboardController.class);

    private final CurrentUserService currentUserService;
    private final ParcelRepository parcelRepository;
    private final RecommendationRepository recommendationRepository;
    private final ClimateService climateService;

    public DashboardController(
            CurrentUserService currentUserService,
            ParcelRepository parcelRepository,
            RecommendationRepository recommendationRepository,
            ClimateService climateService) {
        this.currentUserService = currentUserService;
        this.parcelRepository = parcelRepository;
        this.recommendationRepository = recommendationRepository;
        this.climateService = climateService;
    }

    @GetMapping("/summary")
    public DashboardSummary summary() {
        AppUser user = currentUserService.requireCurrentUser();
        List<Parcel> parcels = parcelRepository.findByUser(user);
        long parcelCount = parcels.size();
        List<Recommendation> history = recommendationRepository.findByParcelUserIdOrderByCreatedAtDesc(user.getId());
        Recommendation latest = history.isEmpty() ? null : history.get(0);
        double totalArea = parcels.stream().mapToDouble(Parcel::getAreaHa).sum();

        if (parcels.isEmpty()) {
            return new DashboardSummary(
                    user.getFullName(),
                    parcelCount,
                    totalArea,
                    latest == null ? null : latest.getSuggestedCrop(),
                    formatAnalysisTime(latest),
                    "Нема парцела",
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    "Нема податоци"
            );
        }

        // Време за последно внесената парцела (најголем id) = координати од таа парцела
        Parcel lastParcel = parcelRepository.findTopByUserOrderByIdDesc(user).orElse(parcels.get(0));
        String weatherLabel = buildWeatherLocationLabel(lastParcel);
        if (!climateService.hasUsableWgs84(lastParcel)) {
            log.warn(
                    "Dashboard: last parcel id={} name={} has no usable WGS84 (lat={} lon={})",
                    lastParcel.getId(), lastParcel.getName(), lastParcel.getLatitude(), lastParcel.getLongitude());
            return new DashboardSummary(
                    user.getFullName(),
                    parcelCount,
                    totalArea,
                    latest == null ? null : latest.getSuggestedCrop(),
                    formatAnalysisTime(latest),
                    weatherLabel,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    "Нема WGS84 — внеси ширина/должина (дец. со точка) за оваа парцела"
            );
        }
        try {
            ClimateData climate = climateService.fetchForecastForParcel(lastParcel);
            return new DashboardSummary(
                    user.getFullName(),
                    parcelCount,
                    totalArea,
                    latest == null ? null : latest.getSuggestedCrop(),
                    formatAnalysisTime(latest),
                    weatherLabel,
                    climate.getTemperature(),
                    climate.getPrecipitation(),
                    climate.getEvapotranspiration(),
                    climate.getSoilMoisture(),
                    climate.getSoilTemperature0cm(),
                    soilStatus(climate.getSoilMoisture())
            );
        } catch (Exception e) {
            log.error(
                    "Open-Meteo / ClimateData failed for last parcel id={}, name={}, lat={}, lon={}: {}",
                    lastParcel.getId(), lastParcel.getName(), lastParcel.getLatitude(), lastParcel.getLongitude(), e.getMessage());
            return new DashboardSummary(
                    user.getFullName(),
                    parcelCount,
                    totalArea,
                    latest == null ? null : latest.getSuggestedCrop(),
                    formatAnalysisTime(latest),
                    weatherLabel,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    "Open-Meteo: неуспешно вчитување"
            );
        }
    }

    private static String formatAnalysisTime(Recommendation latest) {
        if (latest == null || latest.getCreatedAt() == null) {
            return null;
        }
        return latest.getCreatedAt().toString();
    }

    /** Секогаш го прикажува имињето на парцелата, за да не биде измешано со друга со иста локација. */
    private static String buildWeatherLocationLabel(Parcel p) {
        String name = (p.getName() != null && !p.getName().isBlank()) ? p.getName().trim() : "—";
        if (p.getLocation() != null && !p.getLocation().isBlank()) {
            return name + " — " + p.getLocation().trim();
        }
        if (p.getLatitude() != null && p.getLongitude() != null) {
            return name + " · " + String.format(Locale.US, "%.4f, %.4f (WGS84°)", p.getLatitude(), p.getLongitude());
        }
        return name + " · (нема локација/координати)";
    }

    private String soilStatus(double soilMoisture) {
        if (soilMoisture < 0.10) return "Критично суво";
        if (soilMoisture < 0.20) return "Суво";
        if (soilMoisture <= 0.35) return "Оптимална";
        if (soilMoisture <= 0.45) return "Влажно";
        return "Презаситено";
    }

    public record DashboardSummary(
            String fullName,
            long totalParcels,
            double totalAreaHa,
            String latestSuggestedCrop,
            String latestAnalysisAt,
            String weatherLocation,
            double avgTemperature,
            double avgPrecipitation,
            double avgEt0,
            double avgSoilMoisture,
            double avgSoilTemperature0cm,
            String soilStatus
    ) {}
}
