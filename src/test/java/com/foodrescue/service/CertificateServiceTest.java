package com.foodrescue.service;

import com.foodrescue.entity.CertificateEntity;
import com.foodrescue.entity.HotelEntity;
import com.foodrescue.entity.ListingEntity;
import com.foodrescue.entity.PickupEntity;
import com.foodrescue.entity.enums.FoodDiet;
import com.foodrescue.entity.enums.PickupStatus;
import com.foodrescue.repository.CertificateRepository;
import com.foodrescue.repository.HotelRepository;
import com.foodrescue.repository.ListingRepository;
import com.foodrescue.repository.PickupRepository;
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
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CertificateServiceTest {

    @Mock
    private CertificateRepository certificateRepository;
    @Mock
    private HotelRepository hotelRepository;
    @Mock
    private ListingRepository listingRepository;
    @Mock
    private PickupRepository pickupRepository;

    @InjectMocks
    private CertificateService certificateService;

    private UUID hotelId;
    private HotelEntity hotel;

    @BeforeEach
    void setUp() {
        hotelId = UUID.randomUUID();
        hotel = HotelEntity.builder()
                .id(hotelId)
                .name("The Regency Hotel")
                .build();
    }

    @Test
    @DisplayName("Should generate monthly certificate with correct metrics")
    void testGenerateMonthlyCertificate() {
        LocalDate month = LocalDate.of(2026, 10, 1);
        UUID listingId = UUID.randomUUID();
        UUID ngo1 = UUID.randomUUID();
        UUID ngo2 = UUID.randomUUID();

        ListingEntity listing = ListingEntity.builder()
                .id(listingId)
                .hotelId(hotelId)
                .build();

        Instant completedTime = month.atStartOfDay(ZoneId.systemDefault()).plusDays(10).toInstant();

        PickupEntity p1 = PickupEntity.builder()
                .id(UUID.randomUUID())
                .listingId(listingId)
                .ngoId(ngo1)
                .status(PickupStatus.COMPLETED)
                .portionsReceived(100)
                .completedAt(completedTime)
                .voided(false)
                .build();

        PickupEntity p2 = PickupEntity.builder()
                .id(UUID.randomUUID())
                .listingId(listingId)
                .ngoId(ngo2)
                .status(PickupStatus.COMPLETED)
                .portionsReceived(150)
                .completedAt(completedTime)
                .voided(false)
                .build();

        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));
        when(listingRepository.findByHotelIdOrderByCreatedAtDesc(hotelId)).thenReturn(List.of(listing));
        when(pickupRepository.findAll()).thenReturn(List.of(p1, p2));
        when(certificateRepository.findByHotelIdAndMonth(hotelId, month)).thenReturn(Optional.empty());
        when(certificateRepository.save(any(CertificateEntity.class))).thenAnswer(i -> i.getArgument(0));

        CertificateEntity cert = certificateService.generateMonthlyCertificate(hotelId, month);

        assertNotNull(cert);
        assertEquals(hotelId, cert.getHotelId());
        assertEquals(month, cert.getMonth());
        assertEquals(250, cert.getMeals()); // 100 + 150
        assertEquals(2, cert.getNgosServed());
        assertEquals(BigDecimal.valueOf(125.0).setScale(2), cert.getCo2KgAvoided()); // 250 * 0.5 = 125.0 kg CO2
        assertTrue(cert.getPdfPath().contains(hotelId.toString()));
    }

    @Test
    @DisplayName("Should generate formatted HTML certificate string")
    void testGenerateHtmlCertificate() {
        CertificateEntity cert = CertificateEntity.builder()
                .id(UUID.randomUUID())
                .hotelId(hotelId)
                .month(LocalDate.of(2026, 10, 1))
                .meals(500)
                .ngosServed(3)
                .co2KgAvoided(BigDecimal.valueOf(250.0))
                .build();

        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));

        String html = certificateService.generateHtmlCertificate(cert);

        assertNotNull(html);
        assertTrue(html.contains("The Regency Hotel"));
        assertTrue(html.contains("500"));
        assertTrue(html.contains("250.0 kg"));
        assertTrue(html.contains("Certificate of Environmental & Community Impact"));
    }
}
