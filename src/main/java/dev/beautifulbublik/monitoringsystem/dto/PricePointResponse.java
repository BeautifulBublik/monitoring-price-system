package dev.beautifulbublik.monitoringsystem.dto;

import dev.beautifulbublik.monitoringsystem.entity.PriceHistory;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "A point on the price chart")
public record PricePointResponse(

        @Schema(example = "89990.00")
        BigDecimal price,

        @Schema(example = "UAN")
        String currency,

        @Schema(example = "2026-07-13T10:15:30Z")
        Instant checkedAt
) {

    public static PricePointResponse of(PriceHistory history) {
        return new PricePointResponse(history.getPrice(), history.getCurrency(), history.getCheckedAt());
    }
}
