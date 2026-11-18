package com.foodrescue.controller;

import com.foodrescue.dto.ApiResponse;
import com.foodrescue.entity.NgoEntity;
import com.foodrescue.entity.PickupEntity;
import com.foodrescue.entity.ProfileEntity;
import com.foodrescue.entity.VolunteerEntity;
import com.foodrescue.entity.enums.PickupStatus;
import com.foodrescue.entity.enums.UserRole;
import com.foodrescue.repository.NgoRepository;
import com.foodrescue.repository.PickupRepository;
import com.foodrescue.repository.ProfileRepository;
import com.foodrescue.repository.VolunteerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/ngos")
@RequiredArgsConstructor
@Slf4j
public class NgoDashboardController {

    private final NgoRepository ngoRepository;
    private final PickupRepository pickupRepository;
    private final VolunteerRepository volunteerRepository;
    private final ProfileRepository profileRepository;

    @GetMapping("/{ngoId}/dashboard")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDashboard(
            @PathVariable("ngoId") UUID ngoId) {
        try {
            NgoEntity ngo = ngoRepository.findById(ngoId).orElse(null);
            if (ngo == null) return ResponseEntity.notFound().build();

            List<PickupEntity> allPickups = pickupRepository.findByNgoIdOrderByCreatedAtDesc(ngoId);
            long activePickups = allPickups.stream()
                    .filter(p -> p.getStatus() == PickupStatus.CLAIMED
                            || p.getStatus() == PickupStatus.ON_THE_WAY
                            || p.getStatus() == PickupStatus.COLLECTED)
                    .count();
            long completedCount = allPickups.stream()
                    .filter(p -> p.getStatus() == PickupStatus.COMPLETED)
                    .count();

            Map<String, Object> dashboard = new HashMap<>();
            dashboard.put("ngo", ngo);
            dashboard.put("activePickups", activePickups);
            dashboard.put("completedPickupsCount", completedCount);
            dashboard.put("trustScore", ngo.getTrustScore());

            return ResponseEntity.ok(ApiResponse.ok(dashboard));
        } catch (Exception e) {
            log.error("Error fetching NGO dashboard for {}", ngoId, e);
            return ResponseEntity.internalServerError().body(ApiResponse.error("Error fetching dashboard"));
        }
    }

    @GetMapping("/{ngoId}/pickups")
    public ResponseEntity<ApiResponse<List<PickupEntity>>> getNgoPickups(
            @PathVariable("ngoId") UUID ngoId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        try {
            NgoEntity ngo = ngoRepository.findById(ngoId).orElse(null);
            if (ngo == null) return ResponseEntity.notFound().build();

            List<PickupEntity> all = pickupRepository.findByNgoIdOrderByCreatedAtDesc(ngoId);
            int from = page * size;
            int to = Math.min(from + size, all.size());
            List<PickupEntity> page_ = from > all.size() ? List.of() : all.subList(from, to);

            return ResponseEntity.ok(ApiResponse.ok(page_));
        } catch (Exception e) {
            log.error("Error fetching pickups for NGO {}", ngoId, e);
            return ResponseEntity.internalServerError().body(ApiResponse.error("Error fetching pickups"));
        }
    }

    @GetMapping("/{ngoId}/volunteers")
    public ResponseEntity<ApiResponse<List<VolunteerEntity>>> getNgoVolunteers(
            @PathVariable("ngoId") UUID ngoId) {
        try {
            NgoEntity ngo = ngoRepository.findById(ngoId).orElse(null);
            if (ngo == null) return ResponseEntity.notFound().build();

            return ResponseEntity.ok(ApiResponse.ok(volunteerRepository.findByNgoId(ngoId)));
        } catch (Exception e) {
            log.error("Error fetching volunteers for NGO {}", ngoId, e);
            return ResponseEntity.internalServerError().body(ApiResponse.error("Error fetching volunteers"));
        }
    }

    @PostMapping("/{ngoId}/volunteers")
    public ResponseEntity<ApiResponse<VolunteerEntity>> addVolunteer(
            @PathVariable("ngoId") UUID ngoId,
            @RequestBody Map<String, String> body) {
        try {
            NgoEntity ngo = ngoRepository.findById(ngoId).orElse(null);
            if (ngo == null) return ResponseEntity.notFound().build();

            String name = body.getOrDefault("name", "Volunteer");
            String email = body.get("email");
            String phone = body.get("phone");

            // Create a profile for the volunteer
            UUID profileId = UUID.randomUUID();
            ProfileEntity profile = ProfileEntity.builder()
                    .id(profileId)
                    .role(UserRole.VOLUNTEER)
                    .fullName(name)
                    .email(email)
                    .phone(phone)
                    .build();
            profileRepository.save(profile);

            // Create volunteer record
            VolunteerEntity volunteer = VolunteerEntity.builder()
                    .profileId(profileId)
                    .ngoId(ngoId)
                    .name(name)
                    .phone(phone)
                    .active(true)
                    .build();
            volunteer = volunteerRepository.save(volunteer);

            return ResponseEntity.ok(ApiResponse.ok("Volunteer added", volunteer));
        } catch (Exception e) {
            log.error("Error adding volunteer to NGO {}", ngoId, e);
            return ResponseEntity.internalServerError().body(ApiResponse.error("Error adding volunteer"));
        }
    }
}
