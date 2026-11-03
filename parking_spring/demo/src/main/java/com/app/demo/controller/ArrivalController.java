package com.app.demo.controller;

import com.app.demo.dto.ArrivalUpdate;
import com.app.demo.model.Reservation;
import com.app.demo.repository.ReservationRepository;
import com.app.demo.repository.SpotStatusRepository;
import com.app.demo.service.SpotEventService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/arrivals")
public class ArrivalController {

    private final ReservationRepository reservationRepository;
    private final SpotStatusRepository spotStatusRepository;
    private final SpotEventService spotEventService;

    public ArrivalController(ReservationRepository reservationRepository,
                             SpotStatusRepository spotStatusRepository,
                             SpotEventService spotEventService) {
        this.reservationRepository = reservationRepository;
        this.spotStatusRepository = spotStatusRepository;
        this.spotEventService = spotEventService;
    }

    @PostMapping("/match")
    public synchronized ResponseEntity<?> matchArrival(@RequestBody Map<String, String> payload) {
        String plateNumber = Reservation.normalizePlateNumber(payload.get("plateNumber"));
        if (plateNumber.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "plateNumber is required"));
        }

        List<Reservation> matches = reservationRepository.findAllByActiveTrue().stream()
                .filter(reservation -> plateNumber.equals(Reservation.normalizePlateNumber(reservation.getPlateNumber())))
                .toList();
        if (matches.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message", "No active reservation for this vehicle"));
        }
        if (matches.size() != 1) {
            return ResponseEntity.status(409).body(Map.of("message", "Multiple active reservations for this vehicle"));
        }

        Reservation reservation = matches.get(0);
        String spotNumber = reservation.getSpotNumber();
        if (spotNumber == null || !spotNumber.matches("SPOT-[1-5]")) {
            return ResponseEntity.status(409).body(Map.of("message", "Reservation has an invalid parking spot"));
        }
        boolean occupied = spotStatusRepository.findById(spotNumber)
                .map(spot -> "occupied".equalsIgnoreCase(spot.getStatus())).orElse(false);
        if (occupied) {
            return ResponseEntity.status(409).body(Map.of("message", "The reserved spot is occupied"));
        }

        boolean firstDetection = reservation.getMatchedAt() == null;
        if (firstDetection) {
            // Keep the booking active during the drive from the gate to the bay.
            reservation.setMatchedAt(LocalDateTime.now().truncatedTo(ChronoUnit.MICROS));
            reservationRepository.save(reservation);
        }
        ArrivalUpdate update = new ArrivalUpdate(reservation.getId(),
                Reservation.normalizePlateNumber(reservation.getPlateNumber()), spotNumber, reservation.getMatchedAt());
        if (firstDetection) {
            // Repository.save has committed before sending the browser its new state.
            spotEventService.publish(spotNumber);
            spotEventService.publishArrival(update);
        }
        return ResponseEntity.ok(update);
    }
}
