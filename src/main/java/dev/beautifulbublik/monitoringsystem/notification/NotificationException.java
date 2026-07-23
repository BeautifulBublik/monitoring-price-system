package dev.beautifulbublik.monitoringsystem.notification;

/** The channel failed to deliver the message. Caught per-channel in NotificationService. */
public class NotificationException extends RuntimeException {

    public NotificationException(String message, Throwable cause) {
        super(message, cause);
    }

    public NotificationException(String message) {
        super(message);
    }
}
