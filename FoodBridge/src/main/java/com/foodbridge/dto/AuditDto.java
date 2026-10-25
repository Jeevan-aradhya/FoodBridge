package com.foodbridge.dto;

import java.time.LocalDateTime;

public record AuditDto(Long id, String actorEmail, String action, String entityType, Long entityId,
                       String details, LocalDateTime createdAt) {
}
