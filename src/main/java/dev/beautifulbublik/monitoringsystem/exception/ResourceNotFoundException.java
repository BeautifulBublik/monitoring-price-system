package dev.beautifulbublik.monitoringsystem.exception;

/**
 * Resource not found — either it does not exist or it belongs to another user.
 * <p>
 * Both cases are deliberately indistinguishable from the outside: a 403 instead of 404 for
 * someone else's product would confirm that a product with that id exists.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
