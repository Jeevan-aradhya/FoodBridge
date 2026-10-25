package com.foodbridge.exception;

import java.time.LocalDateTime;
import java.util.Map;

/** Consistent JSON error body used by every REST endpoint. */
public record ApiError(LocalDateTime timestamp, int status, String error, String message,
                       String path, Map<String, String> fieldErrors) {
}
