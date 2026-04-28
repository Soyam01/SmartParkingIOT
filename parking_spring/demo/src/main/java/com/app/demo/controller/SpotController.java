package com.app.demo.controller;

import com.app.demo.dto.SpotStatusDto;
import com.app.demo.model.Reservation;
import com.app.demo.model.SpotStatus;
import com.app.demo.repository.ReservationRepository;
import com.app.demo.repository.SpotStatusRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/spot")
public class SpotController {

    @Autowired
    private SpotStatusRepository spotStatusRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @PostMapping("/update")
    public ResponseEntity<String> updateStatus(@RequestBody Map<String, String> payload) {
        String status = payload.get("status");

        if (status == null) {
            return ResponseEntity.badRequest().body("Missing 'status'");
        }

        // Normalize status
        String normalized = "occupied".equalsIgnoreCase(status) ? "occupied" : "free";

        // Update or create spot status
        SpotStatus spot = spotStatusRepository.findById("SPOT-1")
                .orElse(new SpotStatus("SPOT-1"));

        spot.setStatus(normalized);
        spot.setLastUpdated(LocalDateTime.now());
        spotStatusRepository.save(spot);

        // Auto-clear reservation when spot becomes occupied
        if ("occupied".equals(normalized)) {
            Optional<Reservation> activeRes = reservationRepository.findFirstByActiveTrue();
            if (activeRes.isPresent()) {
                Reservation res = activeRes.get();
                res.setActive(false);
                res.setMatchedAt(LocalDateTime.now());
                reservationRepository.save(res);
                System.out.println("Reservation auto-cleared for plate: " + res.getPlateNumber());
            }
        }

        return ResponseEntity.ok("Status updated to: " + normalized);
    }
}