package dev.beautifulbublik.monitoringsystem.parser.shop;

import dev.beautifulbublik.monitoringsystem.config.PriceMonitorProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Map;

/**
 * Matches a product URL to a shop rule. For unknown domains it returns the
 * {@code default-rule} — the product can still be added, just with generic selectors.
 */
@Component
@RequiredArgsConstructor
public class ShopRuleRegistry {

    private final PriceMonitorProperties properties;

    public ShopRule resolve(String url) {
        String host = extractHost(url);
        Map<String, ShopRule> shops = properties.getShops();

        String candidate = host;
        while (candidate.contains(".")) {
            ShopRule rule = shops.get(candidate);
            if (rule != null) {
                return rule;
            }
            candidate = candidate.substring(candidate.indexOf('.') + 1);
        }
        return properties.getDefaultRule();
    }

    public String resolveShopName(String url) {
        ShopRule rule = resolve(url);
        if (rule.getName() != null && !rule.getName().isBlank()) {
            return rule.getName();
        }
        return extractHost(url);
    }

    public String extractHost(String url) {
        try {
            String host = new URI(url).getHost();
            if (host == null) {
                throw new IllegalArgumentException("URL has no host: " + url);
            }
            host = host.toLowerCase(Locale.ROOT);
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Malformed URL: " + url, e);
        }
    }
}
