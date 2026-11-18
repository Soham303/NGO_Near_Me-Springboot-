package com.foodrescue.service;

import com.foodrescue.dto.ClaimListingRequest;
import com.foodrescue.dto.CompletionReportRequest;
import com.foodrescue.dto.PickupResponse;
import com.foodrescue.entity.*;
import com.foodrescue.entity.enums.FoodDiet;
import com.foodrescue.entity.enums.ListingStatus;
import com.foodrescue.entity.enums.PickupStatus;
import com.foodrescue.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PickupServiceTest {

    @Mock
    private PickupRepository pickupRepository;
    @Mock
    private ListingRepository listingRepository;
    @Mock
    private HotelRepository hotelRepository;
    @Mock
    private NgoRepository ngoRepository;
    @Mock
    private VolunteerRepository volunteerRepository;
    @Mock
    private LocationPingRepository locationPingRepository;
    @Mock
    private PickupTagRepository pickupTagRepository;
    @Mock
    private FlagRepository flagRepository;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private PickupService pickupService;

    private UUID listingId;
    private UUID ngoId;
    private UUID hotelId;
    private ListingEntity listing;

    @BeforeEach
    void setUp() {
        listingId = UUID.randomUUID();
        ngoId = UUID.randomUUID();
        hotelId = UUID.randomUUID();

        listing = ListingEntity.builder()
                .id(listingId)
                .hotelId(hotelId)
                .title("Evening Buffet")
                .diet(FoodDiet.VEG)
                .portionsListed(40)
                .cookedAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .pickupBy(Instant.now().plus(2, ChronoUnit.HOURS))
                .status(ListingStatus.POSTED)
                .build();
    }

    @Test
    @DisplayName("Should claim listing atomically and transition status")
    void testClaimListingSuccess() {
        ClaimListingRequest request = new ClaimListingRequest();
        request.setListingId(listingId);
        request.setNgoId(ngoId);

        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing));
        when(pickupRepository.save(any(PickupEntity.class))).thenAnswer(i -> {
            PickupEntity p = i.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        PickupResponse response = pickupService.claimListing(request);

        assertNotNull(response);
        assertEquals(ListingStatus.CLAIMED, listing.getStatus());
        assertEquals(PickupStatus.CLAIMED, response.getStatus());
        verify(listingRepository, times(1)).save(listing);
        verify(pickupRepository, times(1)).save(any(PickupEntity.class));
        verify(notificationService, times(1)).notifyClaimConfirmed(any(PickupEntity.class));
    }

    @Test
    @DisplayName("Should reject claim if listing is already claimed")
    void testClaimListingAlreadyClaimed() {
        listing.setStatus(ListingStatus.CLAIMED);
        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing));

        ClaimListingRequest request = new ClaimListingRequest();
        request.setListingId(listingId);
        request.setNgoId(ngoId);

        assertThrows(IllegalStateException.class, () -> pickupService.claimListing(request));
        verify(pickupRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should auto-flag report when quantity mismatch is > 15%")
    void testSubmitCompletionReportWithQuantityMismatch() {
        UUID pickupId = UUID.randomUUID();
        PickupEntity pickup = PickupEntity.builder()
                .id(pickupId)
                .listingId(listingId)
                .ngoId(ngoId)
                .status(PickupStatus.COLLECTED)
                .build();

        when(pickupRepository.findById(pickupId)).thenReturn(Optional.of(pickup));
        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing));

        CompletionReportRequest request = new CompletionReportRequest();
        // Listed 40, received only 20 -> 50% mismatch (> 15%)
        request.setPortionsReceived(20);
        request.setLeftBehind(0);
        request.setPhotoUrl("https://example.com/photo.jpg");
        request.setTags(List.of("ready_on_time"));

        pickupService.submitCompletionReport(pickupId, request);

        assertEquals(PickupStatus.COMPLETED, pickup.getStatus());
        assertEquals(ListingStatus.COMPLETED, listing.getStatus());
        verify(flagRepository, times(1)).save(any(FlagEntity.class));
    }
}
