package com.foodbridge.dto;

import java.time.LocalDateTime;

public record NotificationDto(Long id, String title, String message, String link, boolean read, LocalDateTime createdAt) {
}
