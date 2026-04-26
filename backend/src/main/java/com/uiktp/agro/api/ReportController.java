package com.uiktp.agro.api;

import com.uiktp.agro.model.AppUser;
import com.uiktp.agro.model.Recommendation;
import com.uiktp.agro.repo.RecommendationRepository;
import com.uiktp.agro.service.CurrentUserService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;

@RestController
@RequestMapping("/api/reports")
public class ReportController {
    private final RecommendationRepository recommendationRepository;
    private final CurrentUserService currentUserService;

    public ReportController(RecommendationRepository recommendationRepository, CurrentUserService currentUserService) {
        this.recommendationRepository = recommendationRepository;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/agronomic-plan/{recommendationId}")
    public ResponseEntity<String> agronomicPlan(@PathVariable Long recommendationId) {
        AppUser currentUser = currentUserService.requireCurrentUser();
        Recommendation recommendation = recommendationRepository.findById(recommendationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recommendation not found"));
        if (!Objects.equals(recommendation.getParcel().getUser().getId(), currentUser.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }

        String content = """
                AGRONOMIC REPORT
                ===============================
                Parcel: %s
                Suggested crop: %s
                Expected yield: %.2f t/ha
                Risk level: %s
                Soil moisture: %s
                Irrigation advice: %s
                Explanation: %s
                Created at: %s
                """.formatted(
                recommendation.getParcel().getName(),
                recommendation.getSuggestedCrop(),
                recommendation.getExpectedYieldTonPerHa(),
                recommendation.getRiskLevel(),
                recommendation.getSoilMoistureStatus(),
                recommendation.getIrrigationAdvice(),
                recommendation.getExplanation(),
                recommendation.getCreatedAt()
        );

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=agronomic-plan-" + recommendationId + ".txt")
                .contentType(MediaType.TEXT_PLAIN)
                .body(content);
    }
}
