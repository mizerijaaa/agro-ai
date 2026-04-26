package com.uiktp.agro.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
public class ClimateData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "parcel_id", nullable = false)
    private Parcel parcel;

    private double temperature;
    private double precipitation;
    private double soilMoisture;
    private double evapotranspiration;

    /** просек од hourly soil_temperature_0cm (°C) */
    @Column(name = "soil_temperature_0cm")
    private double soilTemperature0cm;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public Parcel getParcel() { return parcel; }
    public void setParcel(Parcel parcel) { this.parcel = parcel; }
    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }
    public double getPrecipitation() { return precipitation; }
    public void setPrecipitation(double precipitation) { this.precipitation = precipitation; }
    public double getSoilMoisture() { return soilMoisture; }
    public void setSoilMoisture(double soilMoisture) { this.soilMoisture = soilMoisture; }
    public double getEvapotranspiration() { return evapotranspiration; }
    public void setEvapotranspiration(double evapotranspiration) { this.evapotranspiration = evapotranspiration; }
    public double getSoilTemperature0cm() { return soilTemperature0cm; }
    public void setSoilTemperature0cm(double soilTemperature0cm) { this.soilTemperature0cm = soilTemperature0cm; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
