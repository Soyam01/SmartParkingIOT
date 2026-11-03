package com.app.demo.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Locale;

@Entity
@Data
@Table(name = "reservations")
public class Reservation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = false)
    private String plateNumber;     // e.g. "BA12PA3456"

    private boolean active = true;  // Held until the arriving vehicle parks.

    @Column(updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime matchedAt; // Entry recognition time; null while waiting.

    private String spotNumber;

    private LocalDateTime reservedAt;

    public static String normalizePlateNumber(String plateNumber) {
        return plateNumber == null ? "" : plateNumber.replaceAll("[^a-zA-Z0-9]", "").toUpperCase(Locale.ROOT);
    }
}
