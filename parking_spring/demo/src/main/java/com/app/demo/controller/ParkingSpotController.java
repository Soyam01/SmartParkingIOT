package com.app.demo.controller;

import com.app.demo.dto.SpotStatusRequest;
import com.app.demo.repository.ReservationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/parking")
public class ParkingSpotController {

    @Autowired
    private ReservationRepository reservationRepo; // your repo

    // Simple endpoint to update spot status
    @PostMapping("/status")
    public ResponseEntity<String> updateSpotStatus(@RequestBody SpotStatusRequest request) {
        // For now, assume single spot – later you can add spotId
        if ("reserved".equalsIgnoreCase(request.getStatus())) {
            // Optional: create or update reservation
            // For simplicity, just log or set a flag
            System.out.println("Spot marked as RESERVED by ESP32");
            // You can save to DB here if needed
        } else if ("free".equalsIgnoreCase(request.getStatus())) {
            System.out.println("Spot marked as FREE by ESP32");
            // Optional: clear active reservation
            reservationRepo.findAllByActiveTrue().forEach(r -> {
                r.setActive(false);
                reservationRepo.save(r);
            });
        }

        return ResponseEntity.ok("Status updated: " + request.getStatus());
    }
}
