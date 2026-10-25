package com.foodbridge.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ContactForm {
    @NotBlank(message = "Your name is required") @Size(max = 80) private String name;
    @NotBlank(message = "Your email is required") @Email(message = "Enter a valid email") @Size(max = 120) private String email;
    @NotBlank(message = "Write a message") @Size(max = 400, message = "Message must be at most 400 characters") private String message;
}
