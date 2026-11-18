package com.foodrescue.controller;

import com.foodrescue.dto.ApiResponse;
import com.foodrescue.dto.HotelDashboardResponse;
import com.foodrescue.dto.HotelImpactResponse;
import com.foodrescue.dto.ListingResponse;
import com.foodrescue.entity.CertificateEntity;
import com.foodrescue.entity.HotelEntity;
import com.foodrescue.entity.ListingEntity;
import com.foodrescue.entity.PickupEntity;
import com.foodrescue.entity.ScoreSnapshotEntity;
import com.foodrescue.entity.enums.PickupStatus;
import com.foodrescue.repository.CertificateRepository;
import com.foodrescue.repository.HotelRepository;
import com.foodrescue.repository.ListingRepository;
import com.foodrescue.repository.NgoRepository;
import com.foodrescue.repository.PickupRepository;
import com.foodrescue.repository.ScoreSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/hotels")
@RequiredArgsConstructor
@Slf4j
public class HotelDashboardController {

    private final HotelRepository hotelRepository;
    private final ListingRepository listingRepository;
    private final PickupRepository pickupRepository;
    private final ScoreSnapshotRepository scoreSnapshotRepository;
    private final CertificateRepository certificateRepository;
    private final NgoRepository ngoRepository;

    @GetMapping("/{hotelId}/dashboard")
    public ResponseEntity<ApiResponse<HotelDashboardResponse>> getDashboard(
            @PathVariable("hotelId") UUID hotelId) {
        try {
            HotelEntity hotel = hotelRepository.findById(hotelId).orElse(null);
            if (hotel == null) return ResponseEntity.notFound().build();

            List<ScoreSnapshotEntity> scores = scoreSnapshotRepository.findByHotelIdOrderByPeriodEndDesc(hotelId);
            ScoreSnapshotEntity latestScore = scores.isEmpty() ? null : scores.get(0);

            List<ListingEntity> allListings = listingRepository.findByHotelIdOrderByCreatedAtDesc(hotelId);
            Instant cutoff60 = Instant.now().minus(60, ChronoUnit.DAYS);

            // 60-day window stats
            List<ListingEntity> recent60 = allListings.stream()
                    .filter(l -> l.getCreatedAt().isAfter(cutoff60))
                    .toList();
            long pickups60 = 0;
            int portions60 = 0;
            int totalListings60 = recent60.size();
            int claimedListings60 = 0;

            for (ListingEntity listing : recent60) {
                for (PickupEntity p : pickupRepository.findAll()) {
                    if (p.getListingId().equals(listing.getId())
                            && p.getStatus() == PickupStatus.COMPLETED
                            && !p.getVoided()) {
                        pickups60++;
                        portions60 += (p.getPortionsReceived() != null ? p.getPortionsReceived() : 0);
                        claimedListings60++;
                    }
                }
            }

            double claimRate = totalListings60 > 0
                    ? (double) claimedListings60 / totalListings60 * 100.0 : 0.0;

            // Last 20 listings as ListingResponse
            List<ListingResponse> recentListings = allListings.stream()
                    .limit(20)
                    .map(l -> ListingResponse.builder()
                            .id(l.getId())
                            .hotelId(l.getHotelId())
                            .hotelName(hotel.getName())
                            .hotelAddress(hotel.getAddress())
                            .lat(hotel.getLat())
                            .lng(hotel.getLng())
                            .title(l.getTitle())
                            .description(l.getDescription())
                            .diet(l.getDiet())
                            .portionsListed(l.getPortionsListed())
                            .cookedAt(l.getCookedAt())
                            .pickupBy(l.getPickupBy())
                            .packagingNotes(l.getPackagingNotes())
                            .safetyConfirmed(l.getSafetyConfirmed())
                            .status(l.getStatus())
                            .createdAt(l.getCreatedAt())
                            .build())
                    .collect(Collectors.toList());

            HotelDashboardResponse response = HotelDashboardResponse.builder()
                    .hotel(hotel)
                    .latestScore(latestScore)
                    .pickupsLast60Days(pickups60)
                    .portionsRescuedLast60Days(portions60)
                    .claimRateLast60Days(Math.round(claimRate * 10.0) / 10.0)
                    .recentListings(recentListings)
                    .build();

            return ResponseEntity.ok(ApiResponse.ok(response));
        } catch (Exception e) {
            log.error("Error fetching hotel dashboard for {}", hotelId, e);
            return ResponseEntity.internalServerError().body(ApiResponse.error("Error fetching dashboard"));
        }
    }

    @GetMapping("/{hotelId}/impact")
    public ResponseEntity<ApiResponse<HotelImpactResponse>> getImpact(
            @PathVariable("hotelId") UUID hotelId) {
        try {
            HotelEntity hotel = hotelRepository.findById(hotelId).orElse(null);
            if (hotel == null) return ResponseEntity.notFound().build();

            List<PickupEntity> allPickups = pickupRepository.findAll();
            List<ListingEntity> listings = listingRepository.findByHotelIdOrderByCreatedAtDesc(hotelId);

            int totalMeals = 0;
            long uniqueNgos = 0;

            for (ListingEntity listing : listings) {
                for (PickupEntity p : allPickups) {
                    if (p.getListingId().equals(listing.getId())
                            && p.getStatus() == PickupStatus.COMPLETED
                            && !p.getVoided()
                            && p.getPortionsReceived() != null) {
                        totalMeals += p.getPortionsReceived();
                    }
                }
            }

            // Count distinct NGOs that completed a pickup
            uniqueNgos = allPickups.stream()
                    .filter(p -> p.getStatus() == PickupStatus.COMPLETED && !p.getVoided())
                    .filter(p -> listings.stream().anyMatch(l -> l.getId().equals(p.getListingId())))
                    .map(PickupEntity::getNgoId)
                    .distinct()
                    .count();

            double co2Avoided = totalMeals * 0.5;

            List<ScoreSnapshotEntity> scores = scoreSnapshotRepository.findByHotelIdOrderByPeriodEndDesc(hotelId);
            int streakWeeks = scores.isEmpty() ? 0 : (scores.get(0).getStreakWeeks() != null ? scores.get(0).getStreakWeeks() : 0);
            String tierBadge = scores.isEmpty() ? "seed" : scores.get(0).getTier();

            List<CertificateEntity> certs = certificateRepository.findByHotelIdOrderByMonthDesc(hotelId);

            HotelImpactResponse impact = HotelImpactResponse.builder()
                    .totalMealsRescued(totalMeals)
                    .co2KgAvoided(co2Avoided)
                    .ngosServed(uniqueNgos)
                    .streakWeeks(streakWeeks)
                    .tierBadge(tierBadge)
                    .certificates(certs)
                    .build();

            return ResponseEntity.ok(ApiResponse.ok(impact));
        } catch (Exception e) {
            log.error("Error fetching hotel impact for {}", hotelId, e);
            return ResponseEntity.internalServerError().body(ApiResponse.error("Error fetching impact data"));
        }
    }

    @GetMapping("/{hotelId}/listings")
    public ResponseEntity<ApiResponse<List<ListingResponse>>> getHotelListings(
            @PathVariable("hotelId") UUID hotelId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        try {
            HotelEntity hotel = hotelRepository.findById(hotelId).orElse(null);
            if (hotel == null) return ResponseEntity.notFound().build();

            List<ListingEntity> all = listingRepository.findByHotelIdOrderByCreatedAtDesc(hotelId);
            int from = page * size;
            int to = Math.min(from + size, all.size());

            List<ListingResponse> page_ = (from > all.size()) ? List.of() :
                    all.subList(from, to).stream()
                            .map(l -> toListingResponse(l, hotel))
                            .collect(Collectors.toList());

            return ResponseEntity.ok(ApiResponse.ok(page_));
        } catch (Exception e) {
            log.error("Error fetching listings for hotel {}", hotelId, e);
            return ResponseEntity.internalServerError().body(ApiResponse.error("Error fetching listings"));
        }
    }

    @GetMapping("/{id}/public")
    public ResponseEntity<ApiResponse<HotelImpactResponse>> getPublicImpact(
            @PathVariable("id") UUID id) {
        HotelEntity hotel = hotelRepository.findById(id).orElse(null);
        if (hotel == null) return ResponseEntity.notFound().build();
        if (!Boolean.TRUE.equals(hotel.getPublicListing())) {
            return ResponseEntity.status(403).body(ApiResponse.error("This hotel's impact page is not public"));
        }
        return getImpact(id);
    }

    private ListingResponse toListingResponse(ListingEntity l, HotelEntity hotel) {
        return ListingResponse.builder()
                .id(l.getId())
                .hotelId(l.getHotelId())
                .hotelName(hotel.getName())
                .hotelAddress(hotel.getAddress())
                .lat(hotel.getLat())
                .lng(hotel.getLng())
                .title(l.getTitle())
                .description(l.getDescription())
                .diet(l.getDiet())
                .portionsListed(l.getPortionsListed())
                .cookedAt(l.getCookedAt())
                .pickupBy(l.getPickupBy())
                .packagingNotes(l.getPackagingNotes())
                .safetyConfirmed(l.getSafetyConfirmed())
                .status(l.getStatus())
                .createdAt(l.getCreatedAt())
                .build();
    }
}
