package dev.beautifulbublik.monitoringsystem.parser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PriceTextParserTest {

    private static final char NBSP = (char) 0x00A0;

    private static final char NARROW_NBSP = (char) 0x202F;

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @DisplayName("Parses various price formats")
    @CsvSource({
            "'89 990',           89990.00",

            "'1299.5',           1299.50",
            "'$1,299.00',        1299.00",

            "'1.299,00 €',       1299.00",

            "'1,299',            1299.00",
            "'1.299',            1299.00",

    })
    void parsesPriceFormats(String raw, BigDecimal expected) {
        assertThat(PriceTextParser.parsePrice(raw)).isEqualByComparingTo(expected);
    }

    @Test
    @DisplayName("Understands non-breaking and narrow spaces between digit groups")
    void parsesNonBreakingSpaces() {
        assertThat(PriceTextParser.parsePrice("89" + NBSP + "990 ₽"))
                .isEqualByComparingTo(new BigDecimal("89990.00"));

        assertThat(PriceTextParser.parsePrice("1" + NARROW_NBSP + "299"))
                .isEqualByComparingTo(new BigDecimal("1299.00"));
    }

    @Test
    @DisplayName("Always returns scale 2 — as in the NUMERIC(12,2) column")
    void alwaysScaleTwo() {
        assertThat(PriceTextParser.parsePrice("100").scale()).isEqualTo(2);
    }

    @ParameterizedTest
    @DisplayName("Fails if there are no digits in the text")
    @ValueSource(strings = {"Out of stock", "—", " "})
    void failsWithoutDigits(String raw) {
        assertThatThrownBy(() -> PriceTextParser.parsePrice(raw))
                .isInstanceOf(PriceParsingException.class);
    }

    @Test
    @DisplayName("Fails on null")
    void failsOnNull() {
        assertThatThrownBy(() -> PriceTextParser.parsePrice(null))
                .isInstanceOf(PriceParsingException.class);
    }

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @DisplayName("Detects the currency by symbol or ISO code")
    @CsvSource({
            "'1 299 ₴',     UAH",
            "'$1299',       USD",
            "'1299 €',      EUR",
            "'1299 USD',    USD",
            "'1299 UAH',    UAH",
    })
    void detectsCurrency(String raw, String expected) {
        assertThat(PriceTextParser.detectCurrency(raw, "XXX")).isEqualTo(expected);
    }

    @Test
    @DisplayName("With no currency symbol, falls back to the default value")
    void fallsBackToDefaultCurrency() {
        assertThat(PriceTextParser.detectCurrency("1299", "EUR")).isEqualTo("EUR");
    }
}
