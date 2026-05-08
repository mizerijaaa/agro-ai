package com.uiktp.agro.api;

import com.uiktp.agro.dto.CropRanking;
import com.uiktp.agro.dto.ReportClimateSnapshot;
import com.uiktp.agro.model.AppUser;
import com.uiktp.agro.model.ClimateData;
import com.uiktp.agro.model.Parcel;
import com.uiktp.agro.model.Recommendation;
import com.uiktp.agro.repo.ClimateDataRepository;
import com.uiktp.agro.repo.ParcelRepository;
import com.uiktp.agro.repo.RecommendationRepository;
import com.uiktp.agro.service.CurrentUserService;
import com.uiktp.agro.service.RecommendationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {
    private final ParcelRepository parcelRepository;
    private final RecommendationRepository recommendationRepository;
    private final RecommendationService recommendationService;
    private final ClimateDataRepository climateDataRepository;
    private final CurrentUserService currentUserService;

    public RecommendationController(
            ParcelRepository parcelRepository,
            RecommendationRepository recommendationRepository,
            RecommendationService recommendationService,
            ClimateDataRepository climateDataRepository,
            CurrentUserService currentUserService) {
        this.parcelRepository = parcelRepository;
        this.recommendationRepository = recommendationRepository;
        this.recommendationService = recommendationService;
        this.climateDataRepository = climateDataRepository;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/generate/{parcelId}")
    public RecommendationResponse generate(@PathVariable Long parcelId) {
        AppUser currentUser = currentUserService.requireCurrentUser();
        Parcel parcel = parcelRepository.findById(parcelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parcel not found"));
        if (!Objects.equals(parcel.getUser().getId(), currentUser.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
        Recommendation recommendation = recommendationService.generate(parcel);
        return toResponse(recommendationRepository.save(recommendation), null);
    }

    @GetMapping("/history")
    public List<RecommendationResponse> history() {
        AppUser currentUser = currentUserService.requireCurrentUser();
        return recommendationRepository.findByParcelUserIdOrderByCreatedAtDesc(currentUser.getId())
                .stream()
                .map((Recommendation r) -> toResponse(r, null))
                .toList();
    }

    @GetMapping("/{id}")
    public RecommendationResponse getById(@PathVariable Long id) {
        AppUser currentUser = currentUserService.requireCurrentUser();
        Recommendation rec = recommendationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recommendation not found"));
        if (!Objects.equals(rec.getParcel().getUser().getId(), currentUser.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
        ReportClimateSnapshot climate = climateDataRepository.findFirstByParcelIdOrderByCreatedAtDesc(rec.getParcel().getId())
                .map((ClimateData c) -> toClimateSnapshot(c, rec))
                .orElse(null);
        return toResponse(rec, climate);
    }

    private ReportClimateSnapshot toClimateSnapshot(ClimateData c, Recommendation rec) {
        return new ReportClimateSnapshot(
                c.getTemperature(),
                c.getPrecipitation(),
                c.getPrecipitation() * 7.0,
                c.getEvapotranspiration(),
                c.getSoilMoisture(),
                rec.getSoilMoistureStatus() != null ? rec.getSoilMoistureStatus() : "—");
    }

    private List<CropRanking> topCropsFor(Recommendation rec) {
        List<CropRanking> list = recommendationService.parseRankingsJson(rec.getCropRankingsJson());
        if (!list.isEmpty()) {
            return list;
        }
        int p = switch (rec.getRiskLevel() != null ? rec.getRiskLevel() : "MEDIUM") {
            case "LOW" -> 86;
            case "HIGH" -> 62;
            default -> 74;
        };
        String name = rec.getSuggestedCrop() != null ? rec.getSuggestedCrop() : "—";
        return List.of(new CropRanking(1, name, p, rec.getExpectedYieldTonPerHa(), rec.getRiskLevel()));
    }

    private RecommendationResponse toResponse(Recommendation rec, ReportClimateSnapshot climate) {
        Parcel p = rec.getParcel();
        List<CropRanking> top = topCropsFor(rec);
        Integer primarySuit = top.isEmpty() ? null : top.get(0).suitabilityPercent();
        return new RecommendationResponse(
                rec.getId(),
                p.getId(),
                p.getName(),
                rec.getSuggestedCrop(),
                rec.getExpectedYieldTonPerHa(),
                rec.getRiskLevel(),
                rec.getSoilMoistureStatus(),
                rec.getIrrigationAdvice(),
                rec.getExplanation(),
                rec.getCreatedAt(),
                top,
                primarySuit,
                p.getLocation(),
                p.getSoilType(),
                p.getAreaHa(),
                p.getLatitude(),
                p.getLongitude(),
                climate);
    }

    public record RecommendationResponse(
            Long id,
            Long parcelId,
            String parcelName,
            String suggestedCrop,
            double expectedYieldTonPerHa,
            String riskLevel,
            String soilMoistureStatus,
            String irrigationAdvice,
            String explanation,
            java.time.Instant createdAt,
            java.util.List<CropRanking> topCrops,
            Integer primarySuitabilityPercent,
            String parcelLocation,
            String parcelSoilType,
            Double parcelAreaHa,
            Double parcelLatitude,
            Double parcelLongitude,
            ReportClimateSnapshot climate) {
    }
}
