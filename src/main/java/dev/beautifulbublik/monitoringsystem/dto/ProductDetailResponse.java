package dev.beautifulbublik.monitoringsystem.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Product card: tracking parameters + price history")
public record ProductDetailResponse(

        ProductResponse product,

        @Schema(description = "The minimum price over the entire observation period", example = "84990.00")
        BigDecimal minPrice,

        @Schema(description = "Price history in ascending time order")
        List<PricePointResponse> history
) {
}
