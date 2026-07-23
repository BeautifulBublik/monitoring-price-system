package dev.beautifulbublik.monitoringsystem.entity;

/**
 * The product's tracking state.
 * Only ACTIVE products are included in the background price sweep.
 */
public enum TrackingStatus {
    ACTIVE,
    PAUSED
}
