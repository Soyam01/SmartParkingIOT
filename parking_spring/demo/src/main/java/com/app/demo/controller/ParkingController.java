package com.app.demo.controller;

import com.app.demo.model.Reservation;
import com.app.demo.model.SpotStatus;
import com.app.demo.repository.ReservationRepository;
import com.app.demo.repository.SpotStatusRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Controller
public class ParkingController {

    @Autowired
    private SpotStatusRepository spotStatusRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @GetMapping("/")
    public String home(Model model) {
        List<SpotStatus> spots = new ArrayList<>();

        for (int i = 1; i <= 5; i++) {
            String spotId = "SPOT-" + i;

            // Get latest spot data from database
            SpotStatus spot = spotStatusRepository.findById(spotId)
                    .orElseGet(() -> {
                        SpotStatus newSpot = new SpotStatus(spotId);
                        newSpot.setStatus("free");
                        newSpot.setLastUpdated(LocalDateTime.now());
                        return spotStatusRepository.save(newSpot);
                    });

            // === PRIORITY LOGIC: Occupied > Reserved > Free ===
            Optional<Reservation> activeRes = reservationRepository.findFirstBySpotNumberAndActiveTrue(spotId);

            if ("occupied".equalsIgnoreCase(spot.getStatus())) {
                // Sensor says occupied → highest priority
                spot.setStatus("occupied");

                // Auto-clear reservation if car is parked
                if (activeRes.isPresent()) {
                    Reservation res = activeRes.get();
                    res.setActive(false);
                    res.setMatchedAt(LocalDateTime.now());
                    reservationRepository.save(res);
                    System.out.println("Auto-cleared reservation for " + spotId);
                }
            }
            else if (activeRes.isPresent()) {
                // No car, but has active reservation
                spot.setStatus("reserved");
                spot.setReservedPlate(activeRes.get().getPlateNumber());
            }
            else {
                // Default free
                spot.setStatus("free");
                spot.setReservedPlate(null);
            }

            spots.add(spot);
        }

        model.addAttribute("spots", spots);
        return "index";
    }

    // Register / Reserve a spot
    @PostMapping("/register")
    public String registerVehicle(@ModelAttribute Reservation reservation,
                                  RedirectAttributes redirectAttributes) {

        // Check if spot is already reserved
        boolean alreadyReserved = reservationRepository.existsBySpotNumberAndActiveTrue(String.valueOf(reservation.getSpotNumber()));

        if (alreadyReserved) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Spot " + reservation.getSpotNumber() + " is already reserved!");
            return "redirect:/";
        }

        reservation.setActive(true);
        reservation.setReservedAt(LocalDateTime.now());
        reservationRepository.save(reservation);

        redirectAttributes.addFlashAttribute("successMessage",
                "Spot " + reservation.getSpotNumber() + " reserved successfully for vehicle: " + reservation.getPlateNumber());

        return "redirect:/";
    }
}