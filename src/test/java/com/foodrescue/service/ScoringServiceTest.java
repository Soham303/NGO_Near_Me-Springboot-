package com.foodrescue.service;

import com.foodrescue.entity.HotelEntity;
import com.foodrescue.entity.ListingEntity;
import com.foodrescue.entity.PickupEntity;
import com.foodrescue.entity.ScoreSnapshotEntity;
import com.foodrescue.entity.enums.FoodDiet;
import com.foodrescue.entity.enums.ListingStatus;
import com.foodrescue.entity.enums.PickupStatus;
import com.foodrescue.entity.enums.VenueType;
import com.foodrescue.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScoringServiceTest {

    @Mock
    private ScoreSnapshotRepository scoreSnapshotRepository;
    @Mock
    private HotelRepository hotelRepository;
    @Mock
    private ListingRepository listingRepository;
    @Mock
    private PickupRepository pickupRepository;
    @Mock
    private PickupTagRepository pickupTagRepository;
    @Mock
    private TagRepository tagRepository;
    @Mock
    private PlatformSettingRepository settingRepository;

    @InjectMocks
    private ScoringService scoringService;

    private UUID hotelId;
    private HotelEntity hotel;

    @BeforeEach
    void setUp() {
        hotelId = UUID.randomUUID();
        hotel = HotelEntity.builder()
                .id(hotelId)
                .name("Grand Palace")
                .slug("grand-palace")
                .venueType(VenueType.HOTEL)
                .rooms(100)
                .seats(80)
                .banquetCapacity(200)
                .eventsPerMonth(4)
                .operatingDaysPerWeek(7)
                .publicListing(true)
                .build();
    }

    @Test
    @DisplayName("Should recompute score for hotel and save snapshot")
    void testRecomputeScoreForHotel() {
        UUID listingId = UUID.randomUUID();
        UUID ngoId = UUID.randomUUID();
        Instant now = Instant.now();

        ListingEntity listing = ListingEntity.builder()
                .id(listingId)
                .hotelId(hotelId)
                .title("Buffet surplus")
                .diet(FoodDiet.VEG)
                .portionsListed(50)
                .cookedAt(now.minus(2, ChronoUnit.HOURS))
                .pickupBy(now.plus(1, ChronoUnit.HOURS))
                .status(ListingStatus.COMPLETED)
                .build();

        PickupEntity pickup = PickupEntity.builder()
                .id(UUID.randomUUID())
                .listingId(listingId)
                .ngoId(ngoId)
                .status(PickupStatus.COMPLETED)
                .claimedAt(now.minus(2, ChronoUnit.HOURS))
                .completedAt(now.minus(1, ChronoUnit.HOURS))
                .portionsReceived(48)
                .leftBehindPortions(2)
                .voided(false)
                .build();

        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));
        when(listingRepository.findByHotelIdOrderByCreatedAtDesc(hotelId)).thenReturn(List.of(listing));
        when(pickupRepository.findAll()).thenReturn(List.of(pickup));
        when(scoreSnapshotRepository.findByHotelIdAndPeriodEnd(eq(hotelId), any(LocalDate.class))).thenReturn(Optional.empty());
        when(scoreSnapshotRepository.save(any(ScoreSnapshotEntity.class))).thenAnswer(i -> i.getArgument(0));

        ScoreSnapshotEntity snapshot = scoringService.recomputeScoreForHotel(hotelId);

        assertNotNull(snapshot);
        assertEquals(hotelId, snapshot.getHotelId());
        assertEquals(1, snapshot.getCompletedPickups());
        assertEquals(48, snapshot.getPortionsRescued());
        assertNotNull(snapshot.getComposite());
        assertNotNull(snapshot.getTier());
        verify(scoreSnapshotRepository, times(1)).save(any(ScoreSnapshotEntity.class));
    }

    @Test
    @DisplayName("Should handle hotel with no pickups gracefully")
    void testRecomputeScoreWithNoPickups() {
        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));
        when(listingRepository.findByHotelIdOrderByCreatedAtDesc(hotelId)).thenReturn(List.of());
        when(pickupRepository.findAll()).thenReturn(List.of());
        when(scoreSnapshotRepository.findByHotelIdAndPeriodEnd(eq(hotelId), any(LocalDate.class))).thenReturn(Optional.empty());
        when(scoreSnapshotRepository.save(any(ScoreSnapshotEntity.class))).thenAnswer(i -> i.getArgument(0));

        ScoreSnapshotEntity snapshot = scoringService.recomputeScoreForHotel(hotelId);

        assertNotNull(snapshot);
        assertEquals(0, snapshot.getCompletedPickups());
        assertEquals(0, snapshot.getPortionsRescued());
        assertEquals("seed", snapshot.getTier());
        assertFalse(snapshot.getEligible());
    }
}
