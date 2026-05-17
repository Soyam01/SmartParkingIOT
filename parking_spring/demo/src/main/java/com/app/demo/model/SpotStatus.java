package com.app.demo.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
public class SpotStatus {
    @Id
    private String spotNumber;   // "SPOT-1", "SPOT-2", etc.

    private String status = "free";  // free / occupied
    private LocalDateTime lastUpdated;
    private String reservedPlate;    // optional

    // Constructors
    public SpotStatus() {}
    public SpotStatus(String spotNumber) {
        this.spotNumber = spotNumber;
        this.status = "free";
    }
}