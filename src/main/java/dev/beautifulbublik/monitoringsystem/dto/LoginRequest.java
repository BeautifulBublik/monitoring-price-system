package dev.beautifulbublik.monitoringsystem.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Login with email and password")
public record LoginRequest(

        @Schema(example = "user@example.com")
        @NotBlank(message = "email is required")
        @Email(message = "invalid email")
        String email,

        @Schema(example = "P@ssw0rd123")
        @NotBlank(message = "password is required")
        String password
) {
}
