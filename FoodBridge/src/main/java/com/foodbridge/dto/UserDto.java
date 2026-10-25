package com.foodbridge.dto;

import java.time.LocalDateTime;

public record UserDto(Long id, String email, String fullName, String phone, String city,
                      String role, String status, LocalDateTime createdAt) {
}
