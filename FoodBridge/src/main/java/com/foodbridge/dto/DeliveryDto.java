package com.foodbridge.dto;

import java.time.LocalDateTime;

/** Delivery task. Addresses/instructions are null until the viewer is entitled to see them. */
public record DeliveryDto(
        Long id, Long requestId, Long donationId, String donationName, int quantity, String unit,
        String status, Long volunteerId, String volunteerName,
        String donorName, String recipientName,
        String pickupCity, String dropOffCity,
        String pickupAddress, String pickupInstructions, String dropOffAddress,
        LocalDateTime pickupDeadline, LocalDateTime createdAt,
        LocalDateTime assignedAt, LocalDateTime pickedUpAt, LocalDateTime deliveredAt) {
}
