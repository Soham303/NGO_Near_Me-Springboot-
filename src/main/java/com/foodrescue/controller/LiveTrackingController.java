package com.foodrescue.controller;

import com.foodrescue.dto.ApiResponse;
import com.foodrescue.dto.LocationUpdateRequest;
import com.foodrescue.entity.LocationPingEntity;
import com.foodrescue.entity.PickupEntity;
import com.foodrescue.repository.LocationPingRepository;
import com.foodrescue.repository.PickupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequiredArgsConstructor
@Slf4j
public class LiveTrackingController {

    private final LocationPingRepository locationPingRepository;
    private final PickupRepository pickupRepository;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * WebSocket STOMP: volunteer broadcasts location update for a pickup.
     * Client sends to /app/pickup/{pickupId}/location
     * Broadcast to /topic/pickup/{pickupId}/location
     */
    @MessageMapping("/pickup/{pickupId}/location")
    public void handleLocationUpdate(
            @DestinationVariable("pickupId") String pickupId,
            @Payload LocationUpdateRequest location) {
        try {
            UUID pId = UUID.fromString(pickupId);
            LocationPingEntity ping = LocationPingEntity.builder()
                    .pickupId(pId)
                    .lat(location.getLat())
                    .lng(location.getLng())
                    .accuracyM(location.getAccuracyM())
                    .build();
            locationPingRepository.save(ping);

            messagingTemplate.convertAndSend(
                    "/topic/pickup/" + pickupId + "/location", location);
        } catch (Exception e) {
            log.error("Error handling WebSocket location update for pickup {}", pickupId, e);
        }
    }

    /**
     * SSE: stream latest location ping for a pickup (polls DB every 3s, timeout 5min).
     */
    @GetMapping(value = "/api/live/{pickupId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamPickupLocation(@PathVariable("pickupId") UUID pickupId) {
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                long lastId = -1L;
                long deadline = System.currentTimeMillis() + 5 * 60 * 1000L;
                while (System.currentTimeMillis() < deadline) {
                    List<LocationPingEntity> pings =
                            locationPingRepository.findByPickupIdOrderByRecordedAtDesc(pickupId);
                    if (!pings.isEmpty() && !pings.get(0).getId().equals(lastId)) {
                        lastId = pings.get(0).getId();
                        LocationPingEntity latest = pings.get(0);
                        emitter.send(SseEmitter.event()
                                .id(String.valueOf(latest.getId()))
                                .name("location")
                                .data(Map.of(
                                        "lat", latest.getLat(),
                                        "lng", latest.getLng(),
                                        "recordedAt", latest.getRecordedAt().toString()
                                )));
                    }
                    Thread.sleep(3000);
                }
                emitter.complete();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                emitter.complete();
            } catch (IOException e) {
                emitter.completeWithError(e);
            } catch (Exception e) {
                log.error("SSE stream error for pickup {}", pickupId, e);
                emitter.completeWithError(e);
            }
        });
        executor.shutdown();
        return emitter;
    }

    /**
     * POST: save a location ping and broadcast via WebSocket.
     */
    @PostMapping("/api/live/{pickupId}/ping")
    public ResponseEntity<ApiResponse<LocationPingEntity>> pingLocation(
            @PathVariable("pickupId") UUID pickupId,
            @RequestBody LocationUpdateRequest location) {
        try {
            PickupEntity pickup = pickupRepository.findById(pickupId).orElse(null);
            if (pickup == null) {
                return ResponseEntity.notFound().build();
            }
            LocationPingEntity ping = LocationPingEntity.builder()
                    .pickupId(pickupId)
                    .lat(location.getLat())
                    .lng(location.getLng())
                    .accuracyM(location.getAccuracyM())
                    .build();
            ping = locationPingRepository.save(ping);

            pickup.setCurrentLat(location.getLat());
            pickup.setCurrentLng(location.getLng());
            if (location.getEtaMinutes() != null) {
                pickup.setEtaMinutes(location.getEtaMinutes());
            }
            pickupRepository.save(pickup);

            messagingTemplate.convertAndSend("/topic/pickup/" + pickupId + "/location", location);

            return ResponseEntity.ok(ApiResponse.ok("Location saved", ping));
        } catch (Exception e) {
            log.error("Error saving location ping for pickup {}", pickupId, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to save location ping"));
        }
    }
}
