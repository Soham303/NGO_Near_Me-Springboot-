package com.foodrescue.controller;

import com.foodrescue.dto.ApiResponse;
import com.foodrescue.dto.ListingResponse;
import com.foodrescue.entity.enums.FoodDiet;
import com.foodrescue.entity.enums.ListingStatus;
import com.foodrescue.service.ListingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListingControllerTest {

    @Mock
    private ListingService listingService;

    @InjectMocks
    private ListingController listingController;

    @Test
    @DisplayName("Should return listings with status filter")
    void testGetListings() {
        ListingResponse listing = ListingResponse.builder()
                .id(UUID.randomUUID())
                .title("Fresh Pastries")
                .portionsListed(25)
                .status(ListingStatus.POSTED)
                .diet(FoodDiet.VEG)
                .build();

        when(listingService.getAllListings(ListingStatus.POSTED)).thenReturn(List.of(listing));

        ResponseEntity<ApiResponse<List<ListingResponse>>> response = listingController.getListings(ListingStatus.POSTED);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().isSuccess());
        assertEquals(1, response.getBody().getData().size());
        assertEquals("Fresh Pastries", response.getBody().getData().get(0).getTitle());
    }

    @Test
    @DisplayName("Should return nearby listings with search parameters")
    void testGetNearbyListings() {
        ListingResponse listing = ListingResponse.builder()
                .id(UUID.randomUUID())
                .title("Biryani Handi")
                .portionsListed(50)
                .distanceKm(3.2)
                .status(ListingStatus.POSTED)
                .diet(FoodDiet.NON_VEG)
                .build();

        when(listingService.getNearbyListings(12.97, 77.59, 10.0, FoodDiet.NON_VEG, 20, 120))
                .thenReturn(List.of(listing));

        ResponseEntity<ApiResponse<List<ListingResponse>>> response =
                listingController.getNearbyListings(12.97, 77.59, 10.0, FoodDiet.NON_VEG, 20, 120);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().isSuccess());
        assertEquals(1, response.getBody().getData().size());
        assertEquals(3.2, response.getBody().getData().get(0).getDistanceKm());
    }
}
