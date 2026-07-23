package dev.beautifulbublik.monitoringsystem.exception;

/** A business-uniqueness violation: email taken, product already tracked. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
