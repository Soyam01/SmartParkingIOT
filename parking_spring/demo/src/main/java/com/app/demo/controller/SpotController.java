package com.app.demo.controller;

import com.app.demo.model.SpotStatus;
import com.app.demo.repository.ReservationRepository;
import com.app.demo.repository.SpotStatusRepository;
import com.app.demo.service.SpotEventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/spot")
public class SpotController {

    @Autowired
    private SpotStatusRepository spotStatusRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private SpotEventService spotEventService;

    @PostMapping("/update")
    public ResponseEntity<String> updateSpotStatus(@RequestParam int spot, @RequestBody Map<String, String> payload) {

        String status = payload.get("status");
        if (spot < 1 || spot > 5 || status == null
                || !("free".equalsIgnoreCase(status) || "occupied".equalsIgnoreCase(status))) {
            return ResponseEntity.badRequest().body("A spot from 1 to 5 and status free or occupied are required");
        }
        status = status.toLowerCase(Locale.ROOT);
        String spotId = "SPOT-" + spot;

        SpotStatus spotStatus = spotStatusRepository.findById(spotId)
                .orElseGet(() -> {
                    SpotStatus newSpot = new SpotStatus(spotId);
                    newSpot.setStatus("free");
                    newSpot.setLastUpdated(LocalDateTime.now());
                    return spotStatusRepository.save(newSpot);
                });

        spotStatus.setStatus(status);
        spotStatus.setLastUpdated(LocalDateTime.now());
        spotStatusRepository.save(spotStatus);

        if ("occupied".equalsIgnoreCase(status)) {
            reservationRepository.findFirstBySpotNumberAndActiveTrue(spotId)
                    .filter(reservation -> reservation.getMatchedAt() != null).ifPresent(reservation -> {
                reservation.setActive(false);
                reservationRepository.save(reservation);
            });
        }

        spotEventService.publish(spotId);

        System.out.println("✅ Updated: " + spotId + " = " + status);
        return ResponseEntity.ok("Updated");
    }
}
