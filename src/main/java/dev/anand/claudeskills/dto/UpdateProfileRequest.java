package dev.anand.claudeskills.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(

        @Size(max = 100, message = "Name must be at most 100 characters")
        String name,

        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
        @Pattern(regexp = "[a-zA-Z0-9._-]+", message = "Username may only contain letters, digits, dots, underscores and hyphens")
        String username,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Size(max = 100, message = "Email must be at most 100 characters")
        String email
) {
}
