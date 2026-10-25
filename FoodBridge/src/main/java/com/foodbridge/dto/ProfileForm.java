package com.foodbridge.dto;

import com.foodbridge.enums.FoodCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProfileForm {

    @NotBlank(message = "Full name is required")
    @Size(max = 120)
    private String fullName;

    @Size(max = 20)
    @Pattern(regexp = "^[0-9+\\- ]*$", message = "Phone can contain digits, +, - and spaces only")
    private String phone;

    @NotBlank(message = "City is required")
    @Size(max = 80)
    private String city;

    @Size(max = 150) private String organizationName;
    @Size(max = 60) private String organizationType;
    @Size(max = 300) private String address;
    @Size(max = 60) private String registrationNumber;
    @Size(max = 500) private String description;
    private List<FoodCategory> preferredCategories = new ArrayList<>();

    @Size(max = 60) private String vehicleType;
    @Size(max = 120) private String availability;
    @Size(max = 120) private String serviceArea;
}
