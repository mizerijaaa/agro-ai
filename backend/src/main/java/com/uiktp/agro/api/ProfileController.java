package com.uiktp.agro.api;

import com.uiktp.agro.model.AppUser;
import com.uiktp.agro.model.Parcel;
import com.uiktp.agro.repo.AppUserRepository;
import com.uiktp.agro.repo.ClimateDataRepository;
import com.uiktp.agro.repo.ParcelRepository;
import com.uiktp.agro.repo.RecommendationRepository;
import com.uiktp.agro.service.CurrentUserService;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {
    /** data URL (base64) ≈ 33% поголемо од суровата датотека; ~450 KB слика → ~600k+ знаци */
    private static final int MAX_AVATAR_CHARS = 1_500_000;
    private static final String DEFAULT_OCCUPATION = "Земјоделец";

    private final AppUserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final ParcelRepository parcelRepository;
    private final ClimateDataRepository climateDataRepository;
    private final RecommendationRepository recommendationRepository;

    public ProfileController(
            AppUserRepository userRepository,
            CurrentUserService currentUserService,
            ParcelRepository parcelRepository,
            ClimateDataRepository climateDataRepository,
            RecommendationRepository recommendationRepository) {
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
        this.parcelRepository = parcelRepository;
        this.climateDataRepository = climateDataRepository;
        this.recommendationRepository = recommendationRepository;
    }

    @GetMapping
    public ProfileResponse getProfile() {
        AppUser user = currentUserService.requireCurrentUser();
        return toResponse(user);
    }

    @PutMapping
    @Transactional
    public ProfileResponse updateProfile(@RequestBody UpdateProfileRequest request) {
        AppUser current = currentUserService.requireCurrentUser();
        AppUser user = userRepository.findById(current.getId())
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "User not found"));
        if (request.fullName() != null && !request.fullName().isBlank()) {
            user.setFullName(request.fullName().trim());
        }
        if (request.occupation() != null) {
            String o = request.occupation().trim();
            user.setOccupation(o.isEmpty() ? DEFAULT_OCCUPATION : o);
        }
        if (Boolean.TRUE.equals(request.clearAvatar())) {
            user.setAvatarData(null);
        } else if (request.avatarData() != null) {
            String a = request.avatarData().trim();
            if (a.isEmpty()) {
                user.setAvatarData(null);
            } else {
                if (a.length() > MAX_AVATAR_CHARS) {
                    throw new org.springframework.web.server.ResponseStatusException(
                            org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE, "Сликата е преголема");
                }
                if (!a.startsWith("data:image/")) {
                    throw new org.springframework.web.server.ResponseStatusException(
                            org.springframework.http.HttpStatus.BAD_REQUEST, "Неважечки формат на слика");
                }
                user.setAvatarData(a);
            }
        }
        userRepository.save(user);
        return toResponse(user);
    }

    /**
     * Брише го корисникот и сите негови парцели (и поврзани klima / препораки).
     * POST /delete-account: исто како DELETE, за с окружења каде DELETE не е поддржан или не е картиран.
     */
    @PostMapping("/delete-account")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void deleteAccountPost() {
        performAccountDeletion();
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void deleteAccount() {
        performAccountDeletion();
    }

    private void performAccountDeletion() {
        AppUser current = currentUserService.requireCurrentUser();
        List<Parcel> parcels = parcelRepository.findByUser(current);
        for (Parcel p : parcels) {
            Long pid = p.getId();
            if (pid == null) {
                continue;
            }
            climateDataRepository.deleteAllByParcelId(pid);
            recommendationRepository.deleteAllByParcelId(pid);
            parcelRepository.delete(p);
        }
        userRepository.delete(current);
    }

    private static ProfileResponse toResponse(AppUser user) {
        String occ = user.getOccupation();
        if (occ == null || occ.isBlank()) {
            occ = DEFAULT_OCCUPATION;
        }
        return new ProfileResponse(
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                occ,
                user.getAvatarData());
    }

    public record UpdateProfileRequest(
            String fullName,
            String occupation,
            String avatarData,
            Boolean clearAvatar) {
    }

    public record ProfileResponse(
            String email,
            String fullName,
            String role,
            String occupation,
            String avatarData) {
    }
}
