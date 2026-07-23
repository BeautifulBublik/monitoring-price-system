package dev.beautifulbublik.monitoringsystem.exception;

/** The request is syntactically valid but violates a business rule. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
