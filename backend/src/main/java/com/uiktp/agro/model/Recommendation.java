package com.uiktp.agro.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "recommendation")
public class Recommendation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "parcel_id", nullable = false)
    private Parcel parcel;

    private String suggestedCrop;
    private double expectedYieldTonPerHa;
    private String riskLevel;
    private String soilMoistureStatus;
    private String irrigationAdvice;
    @Column(length = 1500)
    private String explanation;

    /** JSON: [{rank,cropName,suitabilityPercent,expectedYieldTonPerHa,riskLevel}, …] */
    @Column(name = "crop_rankings_json", length = 4000)
    private String cropRankingsJson;

    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public Parcel getParcel() { return parcel; }
    public void setParcel(Parcel parcel) { this.parcel = parcel; }
    public String getSuggestedCrop() { return suggestedCrop; }
    public void setSuggestedCrop(String suggestedCrop) { this.suggestedCrop = suggestedCrop; }

    /** Не е посебна колона — излез за API = suggestedCrop. */
    @JsonProperty("cropName")
    public String getCropName() {
        return suggestedCrop;
    }

    /** Не е посебна колона — пресметка од riskLevel за API. */
    @JsonProperty("confidence")
    public double getConfidence() {
        if (riskLevel == null) {
            return 0.55;
        }
        return switch (riskLevel) {
            case "LOW" -> 0.88;
            case "MEDIUM" -> 0.72;
            default -> 0.55;
        };
    }

    public double getExpectedYieldTonPerHa() { return expectedYieldTonPerHa; }
    public void setExpectedYieldTonPerHa(double expectedYieldTonPerHa) { this.expectedYieldTonPerHa = expectedYieldTonPerHa; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public String getSoilMoistureStatus() { return soilMoistureStatus; }
    public void setSoilMoistureStatus(String soilMoistureStatus) { this.soilMoistureStatus = soilMoistureStatus; }
    public String getIrrigationAdvice() { return irrigationAdvice; }
    public void setIrrigationAdvice(String irrigationAdvice) { this.irrigationAdvice = irrigationAdvice; }
    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
    public String getCropRankingsJson() { return cropRankingsJson; }
    public void setCropRankingsJson(String cropRankingsJson) { this.cropRankingsJson = cropRankingsJson; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
