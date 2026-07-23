package dev.beautifulbublik.monitoringsystem.notification;

/**
 * A single delivery channel. A new channel (SMS, push) is added with a new implementation —
 * {@code NotificationService} picks it up via injection of the list of beans.
 */
public interface Notifier {
    NotificationChannel channel();
    boolean isEnabled();
    void send(PriceDropNotification notification);
}
