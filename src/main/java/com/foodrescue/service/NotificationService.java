package com.foodrescue.service;

import com.foodrescue.entity.ListingEntity;
import com.foodrescue.entity.NgoEntity;
import com.foodrescue.entity.NotificationEntity;
import com.foodrescue.entity.PickupEntity;
import com.foodrescue.repository.HotelRepository;
import com.foodrescue.repository.ListingRepository;
import com.foodrescue.repository.NgoRepository;
import com.foodrescue.repository.NotificationRepository;
import com.foodrescue.repository.VolunteerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NgoRepository ngoRepository;
    private final HotelRepository hotelRepository;
    private final VolunteerRepository volunteerRepository;
    private final ListingRepository listingRepository;

    /**
     * Notify all NGOs within service radius of the hotel about a new listing.
     */
    @Transactional
    public void notifyNewListing(ListingEntity listing) {
        try {
            double hotelLat = hotelRepository.findById(listing.getHotelId())
                    .map(h -> h.getLat() != null ? h.getLat() : 0.0).orElse(0.0);
            double hotelLng = hotelRepository.findById(listing.getHotelId())
                    .map(h -> h.getLng() != null ? h.getLng() : 0.0).orElse(0.0);

            List<NgoEntity> allNgos = ngoRepository.findAll();
            for (NgoEntity ngo : allNgos) {
                if (ngo.getLat() == null || ngo.getLng() == null) continue;
                double dist = haversineKm(ngo.getLat().doubleValue(), ngo.getLng().doubleValue(),
                        hotelLat, hotelLng);
                double radius = ngo.getServiceRadiusKm() != null
                        ? ngo.getServiceRadiusKm().doubleValue() : 10.0;
                if (dist <= radius) {
                    String payload = String.format(
                            "{\"listingId\":\"%s\",\"title\":\"%s\",\"portions\":%d}",
                            listing.getId(), escape(listing.getTitle()), listing.getPortionsListed());
                    save(ngo.getOwnerId(), "New Food Listing Available",
                            listing.getTitle() + " – " + listing.getPortionsListed() + " portions",
                            "NEW_LISTING", payload);
                }
            }
        } catch (Exception e) {
            log.error("Error sending notifyNewListing", e);
        }
    }

    /**
     * Notify hotel owner that a claim was confirmed.
     */
    @Transactional
    public void notifyClaimConfirmed(PickupEntity pickup) {
        try {
            listingRepository.findById(pickup.getListingId()).ifPresent(listing ->
                    hotelRepository.findById(listing.getHotelId()).ifPresent(hotel ->
                            save(hotel.getOwnerId(), "Claim Confirmed",
                                    "An NGO has claimed your listing.",
                                    "CLAIM_CONFIRMED",
                                    String.format("{\"pickupId\":\"%s\"}", pickup.getId()))));
        } catch (Exception e) {
            log.error("Error sending notifyClaimConfirmed", e);
        }
    }

    /**
     * Notify volunteer that they have been assigned.
     */
    @Transactional
    public void notifyVolunteerAssigned(PickupEntity pickup) {
        try {
            if (pickup.getVolunteerId() == null) return;
            volunteerRepository.findById(pickup.getVolunteerId()).ifPresent(vol ->
                    save(vol.getProfileId(), "You Have Been Assigned a Pickup",
                            "You are assigned to a food pickup.",
                            "VOLUNTEER_ASSIGNED",
                            String.format("{\"pickupId\":\"%s\"}", pickup.getId())));
        } catch (Exception e) {
            log.error("Error sending notifyVolunteerAssigned", e);
        }
    }

    /**
     * Notify hotel owner that volunteer is on the way.
     */
    @Transactional
    public void notifyOnTheWay(PickupEntity pickup) {
        try {
            listingRepository.findById(pickup.getListingId()).ifPresent(listing ->
                    hotelRepository.findById(listing.getHotelId()).ifPresent(hotel ->
                            save(hotel.getOwnerId(), "Volunteer On The Way",
                                    "A volunteer is on their way to your venue.",
                                    "ON_THE_WAY",
                                    String.format("{\"pickupId\":\"%s\"}", pickup.getId()))));
        } catch (Exception e) {
            log.error("Error sending notifyOnTheWay", e);
        }
    }

    /**
     * Notify both hotel owner and NGO owner that pickup was collected.
     */
    @Transactional
    public void notifyCollected(PickupEntity pickup) {
        try {
            listingRepository.findById(pickup.getListingId()).ifPresent(listing ->
                    hotelRepository.findById(listing.getHotelId()).ifPresent(hotel ->
                            save(hotel.getOwnerId(), "Food Collected",
                                    "Food has been collected from your venue.",
                                    "COLLECTED",
                                    String.format("{\"pickupId\":\"%s\"}", pickup.getId()))));
            ngoRepository.findById(pickup.getNgoId()).ifPresent(ngo ->
                    save(ngo.getOwnerId(), "Food Collected",
                            "Your volunteer has collected the food.",
                            "COLLECTED",
                            String.format("{\"pickupId\":\"%s\"}", pickup.getId())));
        } catch (Exception e) {
            log.error("Error sending notifyCollected", e);
        }
    }

    /**
     * Notify volunteer that report is due.
     */
    @Transactional
    public void notifyReportDue(PickupEntity pickup) {
        try {
            if (pickup.getVolunteerId() == null) return;
            volunteerRepository.findById(pickup.getVolunteerId()).ifPresent(vol ->
                    save(vol.getProfileId(), "Report Due",
                            "Please submit your pickup report.",
                            "REPORT_DUE",
                            String.format("{\"pickupId\":\"%s\"}", pickup.getId())));
        } catch (Exception e) {
            log.error("Error sending notifyReportDue", e);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void save(UUID userId, String title, String message, String type, String data) {
        NotificationEntity notif = NotificationEntity.builder()
                .userId(userId)
                .title(title)
                .message(message)
                .type(type)
                .read(false)
                .data(data)
                .build();
        notificationRepository.save(notif);
    }

    private double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        final double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
