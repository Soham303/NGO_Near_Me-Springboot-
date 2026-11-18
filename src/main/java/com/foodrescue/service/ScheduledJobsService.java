package com.foodrescue.service;

import com.foodrescue.entity.FlagEntity;
import com.foodrescue.entity.ListingEntity;
import com.foodrescue.entity.PickupEntity;
import com.foodrescue.entity.enums.FlagStatus;
import com.foodrescue.entity.enums.ListingStatus;
import com.foodrescue.entity.enums.PickupStatus;
import com.foodrescue.repository.FlagRepository;
import com.foodrescue.repository.HotelRepository;
import com.foodrescue.repository.ListingRepository;
import com.foodrescue.repository.LocationPingRepository;
import com.foodrescue.repository.PickupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduledJobsService {

    private final ListingRepository listingRepository;
    private final PickupRepository pickupRepository;
    private final FlagRepository flagRepository;
    private final LocationPingRepository locationPingRepository;
    private final HotelRepository hotelRepository;
    private final ScoringService scoringService;
    private final CertificateService certificateService;

    /**
     * Every minute: expire listings whose pickup_by has passed.
     */
    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void expireListings() {
        try {
            List<ListingEntity> posted = listingRepository.findByStatusOrderByCreatedAtDesc(ListingStatus.POSTED);
            Instant now = Instant.now();
            int count = 0;
            for (ListingEntity listing : posted) {
                if (listing.getPickupBy().isBefore(now)) {
                    listing.setStatus(ListingStatus.EXPIRED);
                    listingRepository.save(listing);
                    count++;
                }
            }
            if (count > 0) {
                log.info("[Scheduler] Expired {} listing(s)", count);
            }
        } catch (Exception e) {
            log.error("[Scheduler] Error in expireListings job", e);
        }
    }

    /**
     * Every 5 minutes: release claimed pickups that are >1 hour old with no startedAt.
     */
    @Scheduled(fixedRate = 300_000)
    @Transactional
    public void releaseNoShowClaims() {
        try {
            List<PickupEntity> claimed = pickupRepository.findByStatus(PickupStatus.CLAIMED);
            Instant cutoff = Instant.now().minus(1, ChronoUnit.HOURS);
            int released = 0;
            for (PickupEntity pickup : claimed) {
                if (pickup.getStartedAt() == null && pickup.getClaimedAt().isBefore(cutoff)) {
                    // Release the listing back to POSTED
                    listingRepository.findById(pickup.getListingId()).ifPresent(listing -> {
                        listing.setStatus(ListingStatus.POSTED);
                        listingRepository.save(listing);
                    });
                    pickupRepository.delete(pickup);
                    released++;
                }
            }
            if (released > 0) {
                log.info("[Scheduler] Released {} no-show claim(s)", released);
            }
        } catch (Exception e) {
            log.error("[Scheduler] Error in releaseNoShowClaims job", e);
        }
    }

    /**
     * Every 15 minutes: flag pickups in COLLECTED status for >4h without a completed report.
     */
    @Scheduled(fixedRate = 900_000)
    @Transactional
    public void flagLateReports() {
        try {
            List<PickupEntity> collected = pickupRepository.findByStatus(PickupStatus.COLLECTED);
            Instant cutoff = Instant.now().minus(4, ChronoUnit.HOURS);
            int flagged = 0;
            for (PickupEntity pickup : collected) {
                if (pickup.getCollectedAt() != null
                        && pickup.getCollectedAt().isBefore(cutoff)
                        && pickup.getCompletedAt() == null) {
                    // Check if already flagged for LATE_REPORT
                    List<FlagEntity> existing = flagRepository.findByPickupId(pickup.getId());
                    boolean alreadyFlagged = existing.stream()
                            .anyMatch(f -> "LATE_REPORT".equals(f.getReason()));
                    if (!alreadyFlagged) {
                        FlagEntity flag = FlagEntity.builder()
                                .pickupId(pickup.getId())
                                .reason("LATE_REPORT")
                                .source("auto")
                                .status(FlagStatus.OPEN)
                                .build();
                        flagRepository.save(flag);
                        flagged++;
                    }
                }
            }
            if (flagged > 0) {
                log.info("[Scheduler] Flagged {} late report(s)", flagged);
            }
        } catch (Exception e) {
            log.error("[Scheduler] Error in flagLateReports job", e);
        }
    }

    /**
     * Daily at 2am: delete location pings older than 30 days.
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void deleteOldLocationPings() {
        try {
            Instant cutoff = Instant.now().minus(30, ChronoUnit.DAYS);
            List<com.foodrescue.entity.LocationPingEntity> old =
                    locationPingRepository.findAll().stream()
                            .filter(p -> p.getRecordedAt() != null && p.getRecordedAt().isBefore(cutoff))
                            .toList();
            locationPingRepository.deleteAll(old);
            log.info("[Scheduler] Deleted {} old location ping(s)", old.size());
        } catch (Exception e) {
            log.error("[Scheduler] Error in deleteOldLocationPings job", e);
        }
    }

    /**
     * Every Sunday at 3am: recompute scores for all hotels.
     */
    @Scheduled(cron = "0 0 3 * * SUN")
    @Transactional
    public void weeklyScoreSnapshot() {
        try {
            log.info("[Scheduler] Starting weekly score snapshot...");
            hotelRepository.findAll().forEach(hotel -> {
                try {
                    scoringService.recomputeScoreForHotel(hotel.getId());
                } catch (Exception ex) {
                    log.error("[Scheduler] Failed score recompute for hotel {}", hotel.getId(), ex);
                }
            });
            log.info("[Scheduler] Weekly score snapshot complete");
        } catch (Exception e) {
            log.error("[Scheduler] Error in weeklyScoreSnapshot job", e);
        }
    }

    /**
     * 1st of every month at 1am: generate certificates for all hotels for the previous month.
     */
    @Scheduled(cron = "0 0 1 1 * *")
    @Transactional
    public void generateMonthlyCertificates() {
        try {
            java.time.LocalDate prevMonth = java.time.LocalDate.now().minusMonths(1).withDayOfMonth(1);
            log.info("[Scheduler] Generating monthly certificates for {}", prevMonth);
            certificateService.generateCertificatesForAllHotels(prevMonth);
            log.info("[Scheduler] Monthly certificates generation complete");
        } catch (Exception e) {
            log.error("[Scheduler] Error in generateMonthlyCertificates job", e);
        }
    }
}
