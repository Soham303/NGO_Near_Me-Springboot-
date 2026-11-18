package com.foodrescue.controller;

import com.foodrescue.entity.HotelEntity;
import com.foodrescue.entity.ScoreSnapshotEntity;
import com.foodrescue.repository.HotelRepository;
import com.foodrescue.service.ScoringService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BadgeControllerTest {

    @Mock
    private HotelRepository hotelRepository;
    @Mock
    private ScoringService scoringService;

    @InjectMocks
    private BadgeController badgeController;

    @Test
    @DisplayName("Should generate SVG badge for valid hotel slug")
    void testGetBadgeForHotel() {
        UUID hotelId = UUID.randomUUID();
        HotelEntity hotel = HotelEntity.builder()
                .id(hotelId)
                .name("Grand Palace Hotel")
                .slug("grand-palace-hotel")
                .build();

        ScoreSnapshotEntity score = ScoreSnapshotEntity.builder()
                .hotelId(hotelId)
                .portionsRescued(1800)
                .composite(BigDecimal.valueOf(94.5))
                .tier("forest")
                .build();

        when(hotelRepository.findBySlug("grand-palace-hotel")).thenReturn(Optional.of(hotel));
        when(scoringService.getLatestScoreForHotel(hotelId)).thenReturn(score);

        ResponseEntity<String> response = badgeController.getBadge("grand-palace-hotel");

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getHeaders().getContentType().toString().contains("svg"));
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("<svg"));
        assertTrue(response.getBody().contains("Grand Palace Hotel"));
        assertTrue(response.getBody().contains("FOREST"));
        assertTrue(response.getBody().contains("1,800"));
    }
}
