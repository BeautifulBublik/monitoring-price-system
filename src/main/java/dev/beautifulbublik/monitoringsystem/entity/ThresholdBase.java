package dev.beautifulbublik.monitoringsystem.entity;

/**
 * The baseline against which a price drop is measured.
 *
 * <ul>
 *   <li>{@code LAST_PRICE} — compared against the last known price (reacts to every new drop).</li>
 *   <li>{@code MIN_PRICE} — compared against the historical minimum (notifies only about a genuinely new low).</li>
 * </ul>
 */
public enum ThresholdBase {
    LAST_PRICE,
    MIN_PRICE
}
