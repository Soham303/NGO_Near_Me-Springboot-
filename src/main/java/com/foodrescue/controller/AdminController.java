package com.foodrescue.controller;

import com.foodrescue.dto.ApiResponse;
import com.foodrescue.entity.FlagEntity;
import com.foodrescue.entity.HotelEntity;
import com.foodrescue.entity.NgoEntity;
import com.foodrescue.entity.PickupEntity;
import com.foodrescue.entity.PlatformSettingEntity;
import com.foodrescue.entity.AdminActionEntity;
import com.foodrescue.entity.enums.FlagStatus;
import com.foodrescue.entity.enums.ListingStatus;
import com.foodrescue.entity.enums.OrgStatus;
import com.foodrescue.entity.enums.PickupStatus;
import com.foodrescue.repository.AdminActionRepository;
import com.foodrescue.repository.FlagRepository;
import com.foodrescue.repository.HotelRepository;
import com.foodrescue.repository.ListingRepository;
import com.foodrescue.repository.NgoRepository;
import com.foodrescue.repository.PickupRepository;
import com.foodrescue.repository.PlatformSettingRepository;
import com.foodrescue.security.UserPrincipal;
import com.foodrescue.service.ScoringService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final HotelRepository hotelRepository;
    private final NgoRepository ngoRepository;
    private final AdminActionRepository adminActionRepository;
    private final FlagRepository flagRepository;
    private final PickupRepository pickupRepository;
    private final ListingRepository listingRepository;
    private final PlatformSettingRepository platformSettingRepository;
    private final ScoringService scoringService;

    // ── Existing endpoints ────────────────────────────────────────────────────

    @GetMapping("/actions")
    public ResponseEntity<ApiResponse<List<AdminActionEntity>>> getAdminActions() {
        return ResponseEntity.ok(ApiResponse.ok(adminActionRepository.findAllByOrderByCreatedAtDesc()));
    }

    @PostMapping("/org/{type}/{id}/status")
    public ResponseEntity<ApiResponse<Void>> updateOrgStatus(
            @PathVariable("type") String type,
            @PathVariable("id") UUID id,
            @RequestParam("status") OrgStatus status,
            @RequestParam(value = "reason", required = false) String reason,
            @AuthenticationPrincipal UserPrincipal principal) {

        UUID adminId = principal != null ? principal.getId() : UUID.randomUUID();

        if ("hotel".equalsIgnoreCase(type)) {
            HotelEntity hotel = hotelRepository.findById(id).orElse(null);
            if (hotel == null) return ResponseEntity.notFound().build();
            hotel.setStatus(status);
            hotelRepository.save(hotel);
        } else if ("ngo".equalsIgnoreCase(type)) {
            NgoEntity ngo = ngoRepository.findById(id).orElse(null);
            if (ngo == null) return ResponseEntity.notFound().build();
            ngo.setStatus(status);
            ngoRepository.save(ngo);
        }

        adminActionRepository.save(AdminActionEntity.builder()
                .adminId(adminId)
                .action("UPDATE_STATUS")
                .targetType(type)
                .targetId(id)
                .reason(reason != null ? reason : "Status changed to " + status)
                .build());

        return ResponseEntity.ok(ApiResponse.ok("Status updated successfully", null));
    }

    // ── New endpoints ──────────────────────────────────────────────────────────

    @GetMapping("/pending/hotels")
    public ResponseEntity<ApiResponse<List<HotelEntity>>> getPendingHotels() {
        return ResponseEntity.ok(ApiResponse.ok(hotelRepository.findByStatus(OrgStatus.PENDING)));
    }

    @GetMapping("/pending/ngos")
    public ResponseEntity<ApiResponse<List<NgoEntity>>> getPendingNgos() {
        return ResponseEntity.ok(ApiResponse.ok(ngoRepository.findByStatus(OrgStatus.PENDING)));
    }

    @GetMapping("/flags")
    public ResponseEntity<ApiResponse<List<FlagEntity>>> getOpenFlags() {
        return ResponseEntity.ok(ApiResponse.ok(flagRepository.findByStatus(FlagStatus.OPEN)));
    }

    @PostMapping("/flags/{flagId}/resolve")
    public ResponseEntity<ApiResponse<Void>> resolveFlag(
            @PathVariable("flagId") UUID flagId,
            @RequestParam("resolution") FlagStatus resolution,
            @AuthenticationPrincipal UserPrincipal principal) {
        try {
            FlagEntity flag = flagRepository.findById(flagId).orElse(null);
            if (flag == null) return ResponseEntity.notFound().build();

            flag.setStatus(resolution);
            flag.setResolvedAt(Instant.now());
            flagRepository.save(flag);

            if (resolution == FlagStatus.VOIDED) {
                pickupRepository.findById(flag.getPickupId()).ifPresent(pickup -> {
                    pickup.setVoided(true);
                    pickupRepository.save(pickup);
                    // Recompute score for the hotel
                    listingRepository.findById(pickup.getListingId()).ifPresent(listing -> {
                        try {
                            scoringService.recomputeScoreForHotel(listing.getHotelId());
                        } catch (Exception ex) {
                            log.error("Score recompute failed after void for pickup {}", pickup.getId(), ex);
                        }
                    });
                });
            }

            UUID adminId = principal != null ? principal.getId() : UUID.randomUUID();
            adminActionRepository.save(AdminActionEntity.builder()
                    .adminId(adminId)
                    .action("RESOLVE_FLAG")
                    .targetType("flag")
                    .targetId(flagId)
                    .reason("Flag resolved: " + resolution)
                    .build());

            return ResponseEntity.ok(ApiResponse.ok("Flag resolved", null));
        } catch (Exception e) {
            log.error("Error resolving flag {}", flagId, e);
            return ResponseEntity.internalServerError().body(ApiResponse.error("Error resolving flag"));
        }
    }

    @PostMapping("/settings")
    public ResponseEntity<ApiResponse<PlatformSettingEntity>> saveSetting(
            @RequestBody Map<String, String> body) {
        String key = body.get("key");
        String value = body.get("value");
        if (key == null || value == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("key and value are required"));
        }
        PlatformSettingEntity setting = PlatformSettingEntity.builder()
                .key(key)
                .value(value)
                .build();
        setting = platformSettingRepository.save(setting);
        return ResponseEntity.ok(ApiResponse.ok("Setting saved", setting));
    }

    @GetMapping("/settings")
    public ResponseEntity<ApiResponse<List<PlatformSettingEntity>>> getSettings() {
        return ResponseEntity.ok(ApiResponse.ok(platformSettingRepository.findAll()));
    }

    @GetMapping("/metrics")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMetrics() {
        try {
            long totalHotels = hotelRepository.count();
            long totalNgos = ngoRepository.count();

            List<PickupEntity> allPickups = pickupRepository.findAllByOrderByCreatedAtDesc();
            long totalPickups = allPickups.size();
            long completedPickups = allPickups.stream()
                    .filter(p -> p.getStatus() == PickupStatus.COMPLETED && !p.getVoided())
                    .count();
            long activePickups = allPickups.stream()
                    .filter(p -> p.getStatus() == PickupStatus.CLAIMED
                            || p.getStatus() == PickupStatus.ON_THE_WAY
                            || p.getStatus() == PickupStatus.COLLECTED)
                    .count();

            int totalMeals = allPickups.stream()
                    .filter(p -> p.getStatus() == PickupStatus.COMPLETED && !p.getVoided())
                    .mapToInt(p -> p.getPortionsReceived() != null ? p.getPortionsReceived() : 0)
                    .sum();

            long totalListings = listingRepository.count();
            List<com.foodrescue.entity.ListingEntity> completedListings =
                    listingRepository.findByStatusOrderByCreatedAtDesc(ListingStatus.COMPLETED);
            double claimRate = totalListings > 0
                    ? (double) completedListings.size() / totalListings * 100.0
                    : 0.0;

            Map<String, Object> metrics = new HashMap<>();
            metrics.put("totalHotels", totalHotels);
            metrics.put("totalNgos", totalNgos);
            metrics.put("totalPickups", totalPickups);
            metrics.put("totalMealsRescued", totalMeals);
            metrics.put("claimRatePercent", Math.round(claimRate * 10.0) / 10.0);
            metrics.put("activePickups", activePickups);

            return ResponseEntity.ok(ApiResponse.ok(metrics));
        } catch (Exception e) {
            log.error("Error computing admin metrics", e);
            return ResponseEntity.internalServerError().body(ApiResponse.error("Error fetching metrics"));
        }
    }
}
