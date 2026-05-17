package com.app.demo.controller;

import com.app.demo.model.SpotStatus;
import com.app.demo.repository.SpotStatusRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/spot")
public class SpotController {

    @Autowired
    private SpotStatusRepository spotStatusRepository;

    @PostMapping("/update")
    public ResponseEntity<String> updateSpotStatus(@RequestParam int spot, @RequestBody Map<String, String> payload) {

        String status = payload.get("status");
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

        System.out.println("✅ Updated: " + spotId + " = " + status);
        return ResponseEntity.ok("Updated");
    }
}