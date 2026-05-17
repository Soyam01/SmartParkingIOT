package com.app.demo.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "reservations")
public class Reservation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = false)
    private String plateNumber;     // e.g. "BA12PA3456"

    private boolean active = true;  // true = currently reserved / waiting

    @Column(updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime matchedAt; // null until matched

    private String spotNumber;

    private LocalDateTime reservedAt;
}