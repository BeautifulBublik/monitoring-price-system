package dev.beautifulbublik.monitoringsystem.parser;

/**
 * Single error type for all parsing failures: site unreachable, timeout,
 * changed markup (selector not found), anti-bot stub, unreadable price.
 * <p>
 * The scheduler catches exactly this, logs it and continues checking the remaining products.
 */
public class PriceParsingException extends RuntimeException {

    public PriceParsingException(String message) {
        super(message);
    }

    public PriceParsingException(String message, Throwable cause) {
        super(message, cause);
    }
}
