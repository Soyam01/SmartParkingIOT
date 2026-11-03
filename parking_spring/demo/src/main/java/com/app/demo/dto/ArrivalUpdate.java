package com.app.demo.dto;

import java.time.LocalDateTime;

public record ArrivalUpdate(
        Long reservationId,
        String plateNumber,
        String spotNumber,
        LocalDateTime matchedAt) {
}
