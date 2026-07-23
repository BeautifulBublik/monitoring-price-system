package dev.beautifulbublik.monitoringsystem.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "The issued access token")
public record AuthResponse(

        @Schema(example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyQGV4YW1wbGUuY29tIn0.xxx")
        String token,

        @Schema(example = "Bearer")
        String tokenType,

        @Schema(description = "Token lifetime in seconds", example = "86400")
        long expiresInSeconds
) {
    public static AuthResponse bearer(String token, long expiresInSeconds) {
        return new AuthResponse(token, "Bearer", expiresInSeconds);
    }
}
