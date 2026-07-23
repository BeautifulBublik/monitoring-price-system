package dev.beautifulbublik.monitoringsystem.service;

import dev.beautifulbublik.monitoringsystem.entity.Product;
import dev.beautifulbublik.monitoringsystem.entity.ThresholdBase;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Decides whether a price drop is worth a notification. A pure function with no DB or network —
 * all the non-trivial logic of the project is gathered here and covered by unit tests.
 */
@Component
public class ThresholdEvaluator {

    public boolean shouldNotify(Product product,
                                BigDecimal previousPrice,
                                BigDecimal minPrice,
                                BigDecimal newPrice) {

        if (previousPrice == null) {
            return false;
        }

        BigDecimal base = baseFor(product, previousPrice, minPrice);
        if (newPrice.compareTo(base) >= 0) {
            return false;
        }

        if (alreadyNotifiedAtOrBelow(product, newPrice)) {
            return false;
        }

        BigDecimal drop = base.subtract(newPrice);
        return switch (product.getThresholdType()) {
            case ANY_DROP -> true;
            case ABSOLUTE -> drop.compareTo(thresholdValue(product)) >= 0;
            case PERCENT -> percentOf(drop, base).compareTo(thresholdValue(product)) >= 0;
        };
    }

    private BigDecimal baseFor(Product product, BigDecimal previousPrice, BigDecimal minPrice) {
        if (product.getThresholdBase() == ThresholdBase.MIN_PRICE && minPrice != null) {
            return minPrice;
        }
        return previousPrice;
    }

    private boolean alreadyNotifiedAtOrBelow(Product product, BigDecimal newPrice) {
        BigDecimal lastNotified = product.getLastNotifiedPrice();
        return lastNotified != null && newPrice.compareTo(lastNotified) >= 0;
    }

    private BigDecimal percentOf(BigDecimal drop, BigDecimal base) {
        if (base.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return drop.multiply(BigDecimal.valueOf(100)).divide(base, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal thresholdValue(Product product) {
        BigDecimal value = product.getThresholdValue();
        if (value == null) {
            throw new IllegalStateException(
                    "Product " + product.getId() + " has threshold type " + product.getThresholdType()
                            + " but thresholdValue is not set");
        }
        return value;
    }
}
