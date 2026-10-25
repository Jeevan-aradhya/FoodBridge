package com.foodbridge.dto;

import java.time.LocalDateTime;

/** What the browser/API sees of a donation. pickupAddress/coordinates are null unless the viewer is allowed to see them. */
public record DonationDto(
        Long id, String name, String category, String categoryCode, String description,
        int quantity, int remainingQuantity, String unit,
        LocalDateTime preparedAt, LocalDateTime expiresAt, LocalDateTime pickupDeadline,
        String dietaryInfo, String allergenInfo, String storageInfo,
        String city, String pickupAddress, String pickupInstructions, Double latitude, Double longitude,
        String imageUrl, Long donorId, String donorName,
        String status, boolean available, long minutesLeft, String urgency,
        boolean reported, String reportReason, LocalDateTime createdAt) {
}
