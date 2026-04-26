package com.uiktp.agro.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "parcel")
@JsonIgnoreProperties(ignoreUnknown = true)
public class Parcel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String location;
    private String soilType;
    @Column(name = "area_ha", nullable = false)
    private double areaHa;
    @Column(name = "latitude")
    private Double latitude;
    @Column(name = "longitude")
    private Double longitude;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "parcel_previous_crops", joinColumns = @JoinColumn(name = "parcel_id"))
    @Column(name = "crop", length = 200)
    private List<String> previousCrops = new ArrayList<>();

    @JsonIgnore
    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    // Physical column: legacy "owner_id" (old entity used "owner"). Matches existing H2 schema; avoids SQL errors on insert.
    @JoinColumn(name = "owner_id", nullable = false)
    private AppUser user;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getSoilType() { return soilType; }
    public void setSoilType(String soilType) { this.soilType = soilType; }
    public double getAreaHa() { return areaHa; }
    public void setAreaHa(double areaHa) { this.areaHa = areaHa; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public List<String> getPreviousCrops() {
        if (previousCrops == null) {
            previousCrops = new ArrayList<>();
        }
        return previousCrops;
    }
    public void setPreviousCrops(List<String> previousCrops) {
        this.previousCrops = (previousCrops != null) ? new ArrayList<>(previousCrops) : new ArrayList<>();
    }
    public AppUser getUser() { return user; }
    public void setUser(AppUser user) { this.user = user; }
}
