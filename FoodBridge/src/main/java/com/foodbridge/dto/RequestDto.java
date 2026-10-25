package com.foodbridge.dto;

import java.time.LocalDateTime;

public record RequestDto(
        Long id, Long donationId, String donationName, String categoryCode,
        int quantity, String unit, String status, String message,
        Long recipientId, String recipientName, Long donorId, String donorName,
        String pickupCity, String dropOffAddress, String dropOffCity,
        Long deliveryId, String deliveryStatus,
        LocalDateTime createdAt, LocalDateTime completedAt) {
}
