package com.foodrescue.service;

import com.foodrescue.dto.CreateListingRequest;
import com.foodrescue.dto.ListingResponse;
import com.foodrescue.entity.HotelEntity;
import com.foodrescue.entity.ListingEntity;
import com.foodrescue.entity.enums.FoodDiet;
import com.foodrescue.entity.enums.ListingStatus;
import com.foodrescue.entity.enums.VenueType;
import com.foodrescue.repository.HotelRepository;
import com.foodrescue.repository.ListingRepository;
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
class ListingServiceTest {

    @Mock
    private ListingRepository listingRepository;
    @Mock
    private HotelRepository hotelRepository;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private ListingService listingService;

    private UUID hotelId;
    private HotelEntity hotel;

    @BeforeEach
    void setUp() {
        hotelId = UUID.randomUUID();
        hotel = HotelEntity.builder()
                .id(hotelId)
                .name("Greenview Resort")
                .address("45 MG Road")
                .lat(12.9716)
                .lng(77.5946)
                .venueType(VenueType.HOTEL)
                .build();
    }

    @Test
    @DisplayName("Should create listing successfully and notify NGOs")
    void testCreateListingSuccess() {
        Instant now = Instant.now();
        CreateListingRequest request = new CreateListingRequest();
        request.setHotelId(hotelId);
        request.setTitle("Buffet Curry and Rice");
        request.setDescription("Freshly cooked lunch surplus");
        request.setDiet(FoodDiet.VEG);
        request.setPortionsListed(30);
        request.setCookedAt(now.minus(1, ChronoUnit.HOURS));
        request.setPickupBy(now.plus(2, ChronoUnit.HOURS));
        request.setPackagingNotes("Stainless steel containers");
        request.setSafetyConfirmed(true);

        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));
        when(listingRepository.save(any(ListingEntity.class))).thenAnswer(i -> {
            ListingEntity entity = i.getArgument(0);
            entity.setId(UUID.randomUUID());
            entity.setCreatedAt(Instant.now());
            return entity;
        });

        ListingResponse response = listingService.createListing(request);

        assertNotNull(response);
        assertEquals("Buffet Curry and Rice", response.getTitle());
        assertEquals(30, response.getPortionsListed());
        assertEquals(ListingStatus.POSTED, response.getStatus());
        verify(notificationService, times(1)).notifyNewListing(any(ListingEntity.class));
    }

    @Test
    @DisplayName("Should throw exception if pickupBy is before cookedAt")
    void testCreateListingInvalidTimes() {
        Instant now = Instant.now();
        CreateListingRequest request = new CreateListingRequest();
        request.setHotelId(hotelId);
        request.setTitle("Invalid Time Listing");
        request.setDiet(FoodDiet.VEG);
        request.setPortionsListed(20);
        request.setCookedAt(now);
        request.setPickupBy(now.minus(1, ChronoUnit.HOURS)); // invalid: pickup before cooked

        assertThrows(IllegalArgumentException.class, () -> listingService.createListing(request));
        verify(listingRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should cancel listing when exists")
    void testCancelListing() {
        UUID listingId = UUID.randomUUID();
        ListingEntity listing = ListingEntity.builder()
                .id(listingId)
                .hotelId(hotelId)
                .status(ListingStatus.POSTED)
                .build();

        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing));

        boolean cancelled = listingService.cancelListing(listingId);

        assertTrue(cancelled);
        assertEquals(ListingStatus.CANCELLED, listing.getStatus());
        verify(listingRepository, times(1)).save(listing);
    }
}
