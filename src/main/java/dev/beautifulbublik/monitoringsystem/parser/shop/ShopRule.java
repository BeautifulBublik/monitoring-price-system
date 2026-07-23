package dev.beautifulbublik.monitoringsystem.parser.shop;

import lombok.Getter;
import lombok.Setter;

/**
 * Parsing strategy for a single shop: which CSS selectors to use and how to render the page.
 * Populated from {@code application.yml} (the {@code price-monitor.shops} section), so
 * supporting a new shop is a few lines of config with no rebuild.
 */
@Setter
@Getter
public class ShopRule {

    private String name;
    private String priceSelector;
    private String titleSelector = "h1";
    private String currencySelector;
    private String currency;
    private RenderMode renderMode = RenderMode.AUTO;
    private String waitSelector;

    public String effectiveWaitSelector() {
        return (waitSelector == null || waitSelector.isBlank()) ? priceSelector : waitSelector;
    }
}
