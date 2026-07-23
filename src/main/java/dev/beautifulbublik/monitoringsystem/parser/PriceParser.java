package dev.beautifulbublik.monitoringsystem.parser;

import dev.beautifulbublik.monitoringsystem.parser.shop.ShopRule;

/**
 * Price parser abstraction. Implementations differ only in how the HTML is obtained:
 * {@link JsoupPriceParser} — a plain HTTP request, {@link SeleniumPriceParser} — headless Chrome
 * that executes JavaScript. The CSS selectors themselves come from the outside via {@link ShopRule},
 * so a new shop is added by editing config, not code.
 */
public interface PriceParser {

    String name();

    ParsedPrice parse(String url, ShopRule rule);
}
