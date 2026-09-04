package com.app.demo.dto;

import java.time.LocalDateTime;

public record SpotUpdate(
        String spotNumber,
        String status,
        String reservedPlate,
        LocalDateTime lastUpdated) {
}
