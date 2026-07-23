package dev.beautifulbublik.monitoringsystem.entity;

/**
 * The rule by which a price drop is considered worth a notification.
 *
 * <ul>
 *   <li>{@code ANY_DROP} — notify on any drop, {@code thresholdValue} is not used.</li>
 *   <li>{@code PERCENT} — notify if the price dropped by at least {@code thresholdValue} percent.</li>
 *   <li>{@code ABSOLUTE} — notify if the price dropped by at least {@code thresholdValue} currency units.</li>
 * </ul>
 */
public enum ThresholdType {
    ANY_DROP,
    PERCENT,
    ABSOLUTE
}
