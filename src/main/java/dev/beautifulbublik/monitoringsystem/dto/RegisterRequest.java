package dev.beautifulbublik.monitoringsystem.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Registration of a new user")
public record RegisterRequest(

        @Schema(example = "user@example.com")
        @NotBlank(message = "email is required")
        @Email(message = "invalid email")
        String email,

        @Schema(example = "P@ssw0rd123", minLength = 8)
        @NotBlank(message = "password is required")
        @Size(min = 8, max = 72, message = "password must be between 8 and 72 characters")
        String password
) {
}
