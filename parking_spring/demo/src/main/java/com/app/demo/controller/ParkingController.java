package com.app.demo.controller;

import com.app.demo.model.Reservation;
import com.app.demo.repository.ReservationRepository;
import com.app.demo.repository.SpotStatusRepository;
import com.app.demo.service.SpotEventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;

@Controller
public class ParkingController {

    @Autowired
    private SpotStatusRepository spotStatusRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private SpotEventService spotEventService;

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("spots", spotEventService.getSnapshot());
        return "index";
    }

    // Register / Reserve a spot
    @PostMapping("/register")
    public synchronized String registerVehicle(@ModelAttribute Reservation reservation,
                                   RedirectAttributes redirectAttributes) {
        String spotNumber = reservation.getSpotNumber();
        String plateNumber = Reservation.normalizePlateNumber(reservation.getPlateNumber());
        if (spotNumber == null || !spotNumber.matches("SPOT-[1-5]") || plateNumber.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "A valid parking spot and vehicle number are required");
            return "redirect:/";
        }
        boolean occupied = spotStatusRepository.findById(spotNumber)
                .map(spot -> "occupied".equalsIgnoreCase(spot.getStatus())).orElse(false);
        if (occupied) {
            redirectAttributes.addFlashAttribute("errorMessage", "Spot " + spotNumber + " is occupied");
            return "redirect:/";
        }
        boolean alreadyReserved = reservationRepository.existsBySpotNumberAndActiveTrue(spotNumber);

        if (alreadyReserved) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Spot " + reservation.getSpotNumber() + " is already reserved!");
            return "redirect:/";
        }

        boolean plateAlreadyReserved = reservationRepository.findAllByActiveTrue().stream()
                .anyMatch(active -> plateNumber.equals(Reservation.normalizePlateNumber(active.getPlateNumber())));
        if (plateAlreadyReserved) {
            redirectAttributes.addFlashAttribute("errorMessage", "This vehicle already has an active reservation");
            return "redirect:/";
        }

        reservation.setId(null);
        reservation.setPlateNumber(plateNumber);
        reservation.setActive(true);
        reservation.setMatchedAt(null);
        reservation.setReservedAt(LocalDateTime.now());
        reservationRepository.save(reservation);
        spotEventService.publish(reservation.getSpotNumber());

        redirectAttributes.addFlashAttribute("successMessage",
                "Spot " + reservation.getSpotNumber() + " reserved successfully for vehicle: " + reservation.getPlateNumber());

        return "redirect:/";
    }
}
