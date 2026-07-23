package dev.beautifulbublik.monitoringsystem.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;

@Schema(description = "A single error format for all endpoints")
public record ErrorResponse(

        @Schema(example = "2026-07-13T10:15:30Z")
        Instant timestamp,

        @Schema(example = "400")
        int status,

        @Schema(example = "Bad Request")
        String error,

        @Schema(example = "Request validation error")
        String message,

        @Schema(example = "/api/products")
        String path,

        @Schema(description = "Filled in only for validation errors: field -> reason",
                example = "{\"url\": \"must be a valid URL\"}")
        Map<String, String> fieldErrors
) {
}
