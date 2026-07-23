package dev.beautifulbublik.monitoringsystem.dto;

import dev.beautifulbublik.monitoringsystem.entity.ThresholdBase;
import dev.beautifulbublik.monitoringsystem.entity.ThresholdType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

@Schema(description = """
        Adding a product for tracking.

        Notification threshold:
        - ANY_DROP — any drop (thresholdValue not needed);
        - PERCENT — a drop of at least thresholdValue percent;
        - ABSOLUTE — a drop of at least thresholdValue currency units.
        """)
public record CreateProductRequest(

        @Schema(example = "https://example-shop.com/product/42")
        @NotBlank(message = "url is required")
        @Pattern(regexp = "^https?://.+", message = "url must start with http:// or https://")
        String url,

        @Schema(example = "PERCENT", defaultValue = "ANY_DROP")
        ThresholdType thresholdType,

        @Schema(example = "LAST_PRICE", defaultValue = "LAST_PRICE",
                description = "Measure the drop from the last price or from the historical minimum")
        ThresholdBase thresholdBase,

        @Schema(example = "10.00", description = "Required for PERCENT and ABSOLUTE")
        @DecimalMin(value = "0.01", message = "threshold must be greater than zero")
        BigDecimal thresholdValue
) {

    public ThresholdType typeOrDefault() {
        return thresholdType != null ? thresholdType : ThresholdType.ANY_DROP;
    }

    public ThresholdBase baseOrDefault() {
        return thresholdBase != null ? thresholdBase : ThresholdBase.LAST_PRICE;
    }

    public boolean requiresValue() {
        return typeOrDefault() != ThresholdType.ANY_DROP;
    }
}
