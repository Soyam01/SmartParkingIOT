package com.app.demo.repository;

import com.app.demo.model.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    Optional<Reservation> findByPlateNumberIgnoreCaseAndActiveTrue(String plateNumber);
    List<Reservation> findAllByActiveTrue();
    boolean existsByActiveTrue();               // Is there any active reservation?
    Optional<Reservation> findFirstByActiveTrue(); // Get the current active one

    // Find active reservation for a specific spot
    Optional<Reservation> findFirstBySpotNumberAndActiveTrue(String spotNumber);

    // Check if a spot is already reserved
    boolean existsBySpotNumberAndActiveTrue(String spotNumber);

    // Find all active reservations
    List<Reservation> findByActiveTrue();



}
