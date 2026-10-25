package com.foodbridge.dto;

import com.foodbridge.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterForm {

    @NotBlank(message = "Full name is required")
    @Size(max = 120)
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Enter a valid email address")
    @Size(max = 150)
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "Password must contain a letter and a number")
    private String password;

    @Size(max = 20)
    @Pattern(regexp = "^[0-9+\\- ]*$", message = "Phone can contain digits, +, - and spaces only")
    private String phone;

    @NotBlank(message = "City is required")
    @Size(max = 80)
    private String city;

    @NotNull(message = "Choose how you want to use FoodBridge")
    private Role role;

    /** Business name for donors, organization name for NGOs. */
    @Size(max = 150)
    private String organizationName;

    @Size(max = 60) private String organizationType;
    @Size(max = 300) private String address;

    @Size(max = 60) private String vehicleType;
    @Size(max = 120) private String serviceArea;
}
