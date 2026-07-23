package dev.beautifulbublik.monitoringsystem.dto;

import dev.beautifulbublik.monitoringsystem.entity.ThresholdBase;
import dev.beautifulbublik.monitoringsystem.entity.ThresholdType;
import dev.beautifulbublik.monitoringsystem.entity.TrackingStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

@Schema(description = "Changing tracking parameters. Any field may be omitted — it will keep its previous value.")
public record UpdateProductRequest(

        @Schema(example = "PAUSED", description = "ACTIVE — track, PAUSED — pause")
        TrackingStatus trackingStatus,

        @Schema(example = "PERCENT")
        ThresholdType thresholdType,

        @Schema(example = "MIN_PRICE")
        ThresholdBase thresholdBase,

        @Schema(example = "15.00")
        @DecimalMin(value = "0.01", message = "threshold must be greater than zero")
        BigDecimal thresholdValue
) {
}
