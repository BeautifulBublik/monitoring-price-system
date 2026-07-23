package dev.beautifulbublik.monitoringsystem.parser;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a "human" price from a page ({@code "1 299,00 ₽"}, {@code "$1,299.00"})
 * into a {@link BigDecimal} and detects the currency.
 */
public final class PriceTextParser {

    private static final Pattern SPACES = Pattern.compile("[\\s\\u00A0\\u202F\\u2009]");

    private static final Pattern NOT_NUMERIC = Pattern.compile("[^0-9.,]");

    private static final Pattern ISO_CODE = Pattern.compile("\\b(USD|EUR|UAH)\\b");

    private static final Map<String, String> SYMBOL_TO_CURRENCY = new LinkedHashMap<>();

    static {
        SYMBOL_TO_CURRENCY.put("$", "USD");
        SYMBOL_TO_CURRENCY.put("€", "EUR");
        SYMBOL_TO_CURRENCY.put("₴", "UAH");
    }

    private PriceTextParser() {
    }

    public static BigDecimal parsePrice(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new PriceParsingException("Empty price text");
        }

        String s = SPACES.matcher(raw).replaceAll("");
        s = NOT_NUMERIC.matcher(s).replaceAll("");
        if (s.isEmpty()) {
            throw new PriceParsingException("Price text has no digits: '" + raw + "'");
        }

        int lastSeparator = Math.max(s.lastIndexOf(','), s.lastIndexOf('.'));

        String normalized;
        if (lastSeparator < 0) {
            normalized = s;
        } else {
            String fraction = s.substring(lastSeparator + 1);
            boolean isDecimalSeparator = fraction.matches("\\d{1,2}");
            if (isDecimalSeparator) {
                String integerPart = s.substring(0, lastSeparator).replaceAll("[.,]", "");
                normalized = integerPart + "." + fraction;
            } else {
                normalized = s.replaceAll("[.,]", "");
            }
        }

        try {
            return new BigDecimal(normalized).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            throw new PriceParsingException("Could not parse price: '" + raw + "'", e);
        }
    }

    public static String detectCurrency(String raw, String fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }

        Matcher isoMatcher = ISO_CODE.matcher(raw.toUpperCase(Locale.ROOT));
        if (isoMatcher.find()) {
            return isoMatcher.group(1);
        }

        String lower = raw.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> entry : SYMBOL_TO_CURRENCY.entrySet()) {
            if (lower.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return fallback;
    }
}
