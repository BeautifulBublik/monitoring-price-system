package dev.beautifulbublik.monitoringsystem.parser;

import java.math.BigDecimal;

public record ParsedPrice(String title, BigDecimal price, String currency) {
}
