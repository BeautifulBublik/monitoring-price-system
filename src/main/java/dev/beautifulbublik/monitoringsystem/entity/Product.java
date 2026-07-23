package dev.beautifulbublik.monitoringsystem.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 2048)
    private String url;

    @Column(name = "shop_name", nullable = false)
    private String shopName;

    @Column
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "tracking_status", nullable = false)
    private TrackingStatus trackingStatus = TrackingStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "threshold_type", nullable = false)
    private ThresholdType thresholdType = ThresholdType.ANY_DROP;

    @Enumerated(EnumType.STRING)
    @Column(name = "threshold_base", nullable = false)
    private ThresholdBase thresholdBase = ThresholdBase.LAST_PRICE;

    /** Meaning depends on {@link ThresholdType}: percent or currency units. Not used for ANY_DROP. */
    @Column(name = "threshold_value", precision = 12, scale = 2)
    private BigDecimal thresholdValue;

    /**
     * The price the user has already been notified about dropping to.
     * Used to avoid sending a repeat notification about the same price.
     */
    @Column(name = "last_notified_price", precision = 12, scale = 2)
    private BigDecimal lastNotifiedPrice;

    /**
     * Timestamp of the last successful price check.
     * It exists because history is written only when the price changes
     * (see {@code price-monitor.history.store-mode}) — without this field it would be
     * impossible to tell "the price hasn't changed" from "we haven't checked in a while".
     */
    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;


    public Product(User user, String url, String shopName) {
        this.user = user;
        this.url = url;
        this.shopName = shopName;
        this.createdAt = Instant.now();
    }
}
