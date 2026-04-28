package com.app.demo.controller;

import com.app.demo.model.Reservation;
import com.app.demo.model.SpotStatus;
import com.app.demo.repository.ReservationRepository;
import com.app.demo.repository.SpotStatusRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;

@Controller
public class ParkingController {

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private SpotStatusRepository spotStatusRepository;

    @GetMapping("/")
    public String home(Model model) {
        SpotStatus spot = spotStatusRepository.findById("SPOT-1")
                .orElse(new SpotStatus("SPOT-1"));

        // Auto-clear reservation on occupied
        if ("occupied".equalsIgnoreCase(spot.getStatus())) {
            reservationRepository.findFirstByActiveTrue().ifPresent(res -> {
                res.setActive(false);
                res.setMatchedAt(LocalDateTime.now());
                reservationRepository.save(res);
            });
        }

        model.addAttribute("spotStatus", spot);
        model.addAttribute("isReserved", reservationRepository.existsByActiveTrue());

        if (reservationRepository.existsByActiveTrue()) {
            reservationRepository.findFirstByActiveTrue()
                    .ifPresent(res -> model.addAttribute("reservedPlate", res.getPlateNumber()));
        }

        model.addAttribute("reservation", new Reservation());
        return "index";
    }
    @PostMapping("/register")
    public String registerVehicle(@ModelAttribute Reservation reservation, RedirectAttributes redirectAttributes) {
        if (reservationRepository.existsByActiveTrue()) {
            redirectAttributes.addFlashAttribute("errorMessage", "The spot is reserved");
            return "redirect:/";
        }

        String cleanPlate = reservation.getPlateNumber().toUpperCase().replaceAll("\\s+", "");
        reservation.setPlateNumber(cleanPlate);
        reservation.setActive(true);
        reservationRepository.save(reservation);

        redirectAttributes.addFlashAttribute("successMessage", "Vehicle registered successfully!");
        return "redirect:/";
    }


}