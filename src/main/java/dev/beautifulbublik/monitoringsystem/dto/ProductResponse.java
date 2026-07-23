package dev.beautifulbublik.monitoringsystem.dto;

import dev.beautifulbublik.monitoringsystem.entity.Product;
import dev.beautifulbublik.monitoringsystem.entity.ThresholdBase;
import dev.beautifulbublik.monitoringsystem.entity.ThresholdType;
import dev.beautifulbublik.monitoringsystem.entity.TrackingStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "A product in the tracking list")
public record ProductResponse(

        @Schema(example = "1")
        Long id,

        @Schema(example = "https://example-shop.com/product/42")
        String url,

        @Schema(example = "Example Shop")
        String shopName,

        @Schema(example = "Example Pro 14 Laptop")
        String title,

        @Schema(example = "ACTIVE")
        TrackingStatus trackingStatus,

        @Schema(example = "PERCENT")
        ThresholdType thresholdType,

        @Schema(example = "LAST_PRICE")
        ThresholdBase thresholdBase,

        @Schema(example = "10.00")
        BigDecimal thresholdValue,

        @Schema(description = "Last known price; null if there has not yet been a single successful check",
                example = "89990.00")
        BigDecimal currentPrice,

        @Schema(example = "UAH")
        String currency,

        @Schema(description = "Timestamp of the last successful price check")
        Instant lastCheckedAt,

        Instant createdAt
) {

    public static ProductResponse of(Product product, BigDecimal currentPrice, String currency) {
        return new ProductResponse(
                product.getId(),
                product.getUrl(),
                product.getShopName(),
                product.getTitle(),
                product.getTrackingStatus(),
                product.getThresholdType(),
                product.getThresholdBase(),
                product.getThresholdValue(),
                currentPrice,
                currency,
                product.getLastCheckedAt(),
                product.getCreatedAt());
    }
}
