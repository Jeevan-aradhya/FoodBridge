package com.foodbridge.dto;

import com.foodbridge.enums.FoodCategory;
import com.foodbridge.enums.FoodUnit;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

/** Used by the HTML form (create/edit donation) and by the REST API (JSON body). */
@Getter
@Setter
public class DonationForm {

    @NotBlank(message = "Food name is required")
    @Size(max = 150, message = "Food name must be at most 150 characters")
    private String name;

    @NotNull(message = "Choose a category")
    private FoodCategory category;

    @Size(max = 1000, message = "Description must be at most 1000 characters")
    private String description;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    @Max(value = 100000, message = "Quantity is too large")
    private Integer quantity;

    @NotNull(message = "Choose a unit")
    private FoodUnit unit;

    @NotNull(message = "Preparation time is required")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime preparedAt;

    @NotNull(message = "Expiry time is required")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime expiresAt;

    @NotNull(message = "Pickup deadline is required")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime pickupDeadline;

    @Size(max = 200) private String dietaryInfo;
    @Size(max = 300) private String allergenInfo;
    @Size(max = 300) private String storageInfo;

    @NotBlank(message = "Pickup address is required")
    @Size(max = 300)
    private String pickupAddress;

    @NotBlank(message = "City is required")
    @Size(max = 80)
    private String city;

    private Double latitude;
    private Double longitude;

    @Size(max = 500) private String pickupInstructions;
}
