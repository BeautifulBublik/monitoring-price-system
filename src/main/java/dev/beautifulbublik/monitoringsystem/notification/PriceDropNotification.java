package dev.beautifulbublik.monitoringsystem.notification;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Everything any channel needs to send an email/message about a price drop.
 * Decoupled from JPA entities: notifiers must not touch lazy associations outside a transaction.
 */
public record PriceDropNotification(

        String recipientEmail,
        String telegramChatId,
        String productTitle,
        String productUrl,
        String shopName,
        BigDecimal oldPrice,
        BigDecimal newPrice,
        String currency
) {

    public BigDecimal absoluteDrop() {
        return oldPrice.subtract(newPrice);
    }

    public BigDecimal percentDrop() {
        if (oldPrice.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return absoluteDrop()
                .multiply(BigDecimal.valueOf(100))
                .divide(oldPrice, 1, RoundingMode.HALF_UP);
    }
}
