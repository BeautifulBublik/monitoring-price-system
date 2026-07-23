package dev.beautifulbublik.monitoringsystem.service;

import dev.beautifulbublik.monitoringsystem.entity.Product;
import dev.beautifulbublik.monitoringsystem.entity.ThresholdBase;
import dev.beautifulbublik.monitoringsystem.entity.ThresholdType;
import dev.beautifulbublik.monitoringsystem.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ThresholdEvaluatorTest {

    private ThresholdEvaluator evaluator;
    private Product product;

    @BeforeEach
    void setUp() {
        evaluator = new ThresholdEvaluator();
        product = new Product(new User("user@example.com", "hash"), "https://example-shop.com/p/1", "Example Shop");
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    @Test
    @DisplayName("The first check is not counted as a drop: there is nothing to compare against")
    void firstCheckNeverNotifies() {
        product.setThresholdType(ThresholdType.ANY_DROP);

        assertThat(evaluator.shouldNotify(product, null, null, money("100.00"))).isFalse();
    }

    @Test
    @DisplayName("A price increase does not notify")
    void priceIncreaseDoesNotNotify() {
        product.setThresholdType(ThresholdType.ANY_DROP);

        assertThat(evaluator.shouldNotify(product, money("100.00"), money("100.00"), money("120.00"))).isFalse();
    }

    @Test
    @DisplayName("The price did not change — do not notify")
    void unchangedPriceDoesNotNotify() {
        product.setThresholdType(ThresholdType.ANY_DROP);

        assertThat(evaluator.shouldNotify(product, money("100.00"), money("100.00"), money("100.00"))).isFalse();
    }

    @Nested
    @DisplayName("ANY_DROP")
    class AnyDrop {

        @Test
        @DisplayName("Notifies on any drop, even a tiny one")
        void notifiesOnAnyDrop() {
            product.setThresholdType(ThresholdType.ANY_DROP);

            assertThat(evaluator.shouldNotify(product, money("100.00"), money("100.00"), money("99.99"))).isTrue();
        }
    }

    @Nested
    @DisplayName("PERCENT")
    class Percent {

        @BeforeEach
        void setUp() {
            product.setThresholdType(ThresholdType.PERCENT);
            product.setThresholdValue(money("10"));
        }

        @Test
        @DisplayName("A drop exactly at the threshold notifies (boundary inclusive)")
        void notifiesAtExactThreshold() {
            assertThat(evaluator.shouldNotify(product, money("100.00"), money("100.00"), money("90.00"))).isTrue();
        }

        @Test
        @DisplayName("A drop smaller than the threshold does not notify")
        void silentBelowThreshold() {
            assertThat(evaluator.shouldNotify(product, money("100.00"), money("100.00"), money("90.01"))).isFalse();
        }
    }

    @Nested
    @DisplayName("ABSOLUTE")
    class Absolute {

        @BeforeEach
        void setUp() {
            product.setThresholdType(ThresholdType.ABSOLUTE);
            product.setThresholdValue(money("500"));
        }

        @Test
        @DisplayName("A drop at or above the threshold notifies")
        void notifiesAtOrAboveThreshold() {
            assertThat(evaluator.shouldNotify(product, money("10000.00"), money("10000.00"), money("9500.00")))
                    .isTrue();
        }

        @Test
        @DisplayName("A drop smaller than the threshold does not notify")
        void silentBelowThreshold() {
            assertThat(evaluator.shouldNotify(product, money("10000.00"), money("10000.00"), money("9600.00")))
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("Comparison baseline")
    class Base {

        @Test
        @DisplayName("MIN_PRICE: a drop relative to the last price is ignored if it is not a new low")
        void minPriceBaseIgnoresDropAboveHistoricalMin() {
            product.setThresholdType(ThresholdType.ANY_DROP);
            product.setThresholdBase(ThresholdBase.MIN_PRICE);

            assertThat(evaluator.shouldNotify(product, money("120.00"), money("100.00"), money("110.00")))
                    .isFalse();
        }

        @Test
        @DisplayName("MIN_PRICE: a new historical low notifies")
        void minPriceBaseNotifiesOnNewLow() {
            product.setThresholdType(ThresholdType.ANY_DROP);
            product.setThresholdBase(ThresholdBase.MIN_PRICE);

            assertThat(evaluator.shouldNotify(product, money("120.00"), money("100.00"), money("99.00")))
                    .isTrue();
        }

        @Test
        @DisplayName("LAST_PRICE: the same drop notifies, because the baseline is the last price")
        void lastPriceBaseNotifies() {
            product.setThresholdType(ThresholdType.ANY_DROP);
            product.setThresholdBase(ThresholdBase.LAST_PRICE);

            assertThat(evaluator.shouldNotify(product, money("120.00"), money("100.00"), money("110.00")))
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("Spam guard")
    class NoSpam {

        @BeforeEach
        void setUp() {
            product.setThresholdType(ThresholdType.ANY_DROP);
        }

        @Test
        @DisplayName("Do not notify about the same price a second time")
        void doesNotNotifyTwiceAboutSamePrice() {
            product.setLastNotifiedPrice(money("90.00"));

            assertThat(evaluator.shouldNotify(product, money("100.00"), money("90.00"), money("90.00"))).isFalse();
        }

        @Test
        @DisplayName("Price bounced up and came back to an already-notified value — stay silent")
        void doesNotNotifyOnBounceBackToNotifiedPrice() {
            product.setLastNotifiedPrice(money("90.00"));
            assertThat(evaluator.shouldNotify(product, money("100.00"), money("90.00"), money("90.00"))).isFalse();
        }

        @Test
        @DisplayName("Price dropped even below the already-notified value — notify again")
        void notifiesWhenPriceDropsFurther() {
            product.setLastNotifiedPrice(money("90.00"));

            assertThat(evaluator.shouldNotify(product, money("90.00"), money("90.00"), money("85.00"))).isTrue();
        }
    }
}
