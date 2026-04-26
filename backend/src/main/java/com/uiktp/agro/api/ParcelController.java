package com.uiktp.agro.api;

import com.uiktp.agro.model.AppUser;
import com.uiktp.agro.model.Parcel;
import com.uiktp.agro.repo.AppUserRepository;
import com.uiktp.agro.repo.ClimateDataRepository;
import com.uiktp.agro.repo.ParcelRepository;
import com.uiktp.agro.repo.RecommendationRepository;
import com.uiktp.agro.service.CurrentUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/parcels")
public class ParcelController {
    private static final Logger log = LoggerFactory.getLogger(ParcelController.class);

    private final ParcelRepository parcelRepository;
    private final AppUserRepository appUserRepository;
    private final CurrentUserService currentUserService;
    private final ClimateDataRepository climateDataRepository;
    private final RecommendationRepository recommendationRepository;

    public ParcelController(
            ParcelRepository parcelRepository,
            AppUserRepository appUserRepository,
            CurrentUserService currentUserService,
            ClimateDataRepository climateDataRepository,
            RecommendationRepository recommendationRepository) {
        this.parcelRepository = parcelRepository;
        this.appUserRepository = appUserRepository;
        this.currentUserService = currentUserService;
        this.climateDataRepository = climateDataRepository;
        this.recommendationRepository = recommendationRepository;
    }

    @GetMapping
    public List<Parcel> list() {
        AppUser currentUser = currentUserService.requireCurrentUser();
        log.info("GET /api/parcels — listing parcels for userId={}, email={}", currentUser.getId(), currentUser.getEmail());
        List<Parcel> parcels = parcelRepository.findByUser(currentUser);
        log.info("GET /api/parcels — returned {} parcel(s)", parcels.size());
        return parcels;
    }

    @GetMapping("/{id}")
    public Parcel getOne(@PathVariable Long id) {
        AppUser currentUser = currentUserService.requireCurrentUser();
        Parcel parcel = parcelRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parcel not found"));
        if (!Objects.equals(parcel.getUser().getId(), currentUser.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
        return parcel;
    }

    @PostMapping
    public Parcel create(@RequestBody Parcel parcel) {
        if (parcel == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        AppUser currentUser = currentUserService.requireCurrentUser();
        log.info("POST /api/parcels — create requested by userId={}, name={}", currentUser.getId(), parcel.getName());

        parcel.setId(null);
        normalizeIncomingParcel(parcel);
        if (parcel.getName() == null || !StringUtils.hasText(parcel.getName().trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        }
        if (parcel.getLatitude() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "latitude is required");
        }
        if (parcel.getLongitude() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "longitude is required");
        }

        if (parcel.getPreviousCrops() == null) {
            parcel.setPreviousCrops(new ArrayList<>());
        }

        AppUser managedUser = appUserRepository.findById(currentUser.getId())
                .orElseThrow(() -> {
                    log.error("No AppUser in DB for currentUser id={}", currentUser.getId());
                    return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "User not found");
                });
        parcel.setUser(managedUser);

        try {
            Parcel saved = parcelRepository.save(parcel);
            log.info("POST /api/parcels — saved parcel id={}, user_id={}", saved.getId(), currentUser.getId());
            return saved;
        } catch (Exception e) {
            log.error("Error saving parcel", e);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not save parcel: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()),
                    e
            );
        }
    }

    @DeleteMapping("/{id}")
    @Transactional
    public void delete(@PathVariable Long id) {
        AppUser currentUser = currentUserService.requireCurrentUser();
        Parcel parcel = parcelRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parcel not found"));
        if (!Objects.equals(parcel.getUser().getId(), currentUser.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
        climateDataRepository.deleteAllByParcelId(id);
        recommendationRepository.deleteAllByParcelId(id);
        parcelRepository.delete(parcel);
        log.info("DELETE /api/parcels/{} — success for userId={}", id, currentUser.getId());
    }

    @PutMapping("/{id}")
    public Parcel update(@PathVariable Long id, @RequestBody Parcel incoming) {
        if (incoming == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        AppUser currentUser = currentUserService.requireCurrentUser();
        Parcel parcel = parcelRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parcel not found"));
        if (!Objects.equals(parcel.getUser().getId(), currentUser.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }

        normalizeIncomingParcel(incoming);
        if (StringUtils.hasText(incoming.getName())) {
            parcel.setName(incoming.getName());
        }
        if (incoming.getLocation() != null) {
            parcel.setLocation(incoming.getLocation());
        }
        if (StringUtils.hasText(incoming.getSoilType())) {
            parcel.setSoilType(incoming.getSoilType());
        }
        parcel.setAreaHa(incoming.getAreaHa());
        if (incoming.getLatitude() != null) {
            parcel.setLatitude(incoming.getLatitude());
        }
        if (incoming.getLongitude() != null) {
            parcel.setLongitude(incoming.getLongitude());
        }
        if (incoming.getPreviousCrops() == null) {
            incoming.setPreviousCrops(new ArrayList<>());
        }
        parcel.setPreviousCrops(incoming.getPreviousCrops());
        try {
            return parcelRepository.save(parcel);
        } catch (Exception e) {
            log.error("Error saving parcel", e);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not save parcel: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()),
                    e
            );
        }
    }

    private static void normalizeIncomingParcel(Parcel parcel) {
        if (parcel.getLocation() == null) {
            parcel.setLocation("");
        }
        if (!StringUtils.hasText(parcel.getSoilType())) {
            parcel.setSoilType("—");
        }
        if (parcel.getPreviousCrops() == null) {
            parcel.setPreviousCrops(new ArrayList<>());
        }
    }
}
