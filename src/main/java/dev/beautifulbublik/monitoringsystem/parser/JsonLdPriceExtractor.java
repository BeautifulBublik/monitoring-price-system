package dev.beautifulbublik.monitoringsystem.parser;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;

/**
 * Extracts the price from schema.org microdata in JSON-LD form
 * ({@code <script type="application/ld+json">}).
 * <p>
 * Most modern shops expose a {@code Product} block with an {@code Offer} right in the static HTML
 * for SEO — it already contains ready-made {@code name}, {@code price} and {@code priceCurrency}.
 * This makes the {@code default-rule} work for almost any shop without a per-shop CSS selector.
 */
@Component
public class JsonLdPriceExtractor {

    private static final Logger log = LoggerFactory.getLogger(JsonLdPriceExtractor.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    public record JsonLdPrice(String name, BigDecimal price, String currency) {
    }

    public Optional<JsonLdPrice> extract(Document document) {
        for (Element script : document.getElementsByTag("script")) {
            String type = script.attr("type");
            if (!type.toLowerCase(Locale.ROOT).contains("ld+json")) {
                continue;
            }
            String json = script.data();
            if (json.isBlank()) {
                continue;
            }

            JsonNode root;
            try {
                root = objectMapper.readTree(json);
            } catch (RuntimeException e) {
                log.debug("Skipping unreadable JSON-LD block: {}", e.getMessage());
                continue;
            }

            Optional<JsonLdPrice> found = findProduct(root);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    private Optional<JsonLdPrice> findProduct(JsonNode node) {
        if (node == null) {
            return Optional.empty();
        }
        if (node.isArray()) {
            for (JsonNode element : node) {
                Optional<JsonLdPrice> result = findProduct(element);
                if (result.isPresent()) {
                    return result;
                }
            }
            return Optional.empty();
        }
        if (node.has("@graph")) {
            Optional<JsonLdPrice> result = findProduct(node.get("@graph"));
            if (result.isPresent()) {
                return result;
            }
        }
        if (isProduct(node)) {
            Optional<JsonLdPrice> result = fromProduct(node);
            if (result.isPresent()) {
                return result;
            }
        }
        return Optional.empty();
    }

    private boolean isProduct(JsonNode node) {
        JsonNode type = node.get("@type");
        if (type == null) {
            return false;
        }
        if (type.isArray()) {
            for (JsonNode t : type) {
                if ("Product".equalsIgnoreCase(t.asString(""))) {
                    return true;
                }
            }
            return false;
        }
        return "Product".equalsIgnoreCase(type.asString(""));
    }

    private Optional<JsonLdPrice> fromProduct(JsonNode product) {
        JsonNode offers = product.get("offers");
        if (offers == null) {
            return Optional.empty();
        }

        JsonNode offer = offers.isArray() ? firstOfferWithPrice(offers) : offers;
        if (offer == null) {
            return Optional.empty();
        }

        String priceText = firstNonBlank(offer.path("price").asString(""),
                offer.path("lowPrice").asString(""));
        if (priceText.isBlank()) {
            return Optional.empty();
        }

        BigDecimal price;
        try {
            price = PriceTextParser.parsePrice(priceText);
        } catch (PriceParsingException e) {
            log.debug("JSON-LD price did not parse ('{}'): {}", priceText, e.getMessage());
            return Optional.empty();
        }
        if (price.signum() <= 0) {
            return Optional.empty();
        }

        String currency = emptyToNull(offer.path("priceCurrency").asString(""));
        String name = emptyToNull(product.path("name").asString(""));
        return Optional.of(new JsonLdPrice(name, price, currency));
    }

    private JsonNode firstOfferWithPrice(JsonNode offers) {
        JsonNode fallback = null;
        for (JsonNode offer : offers) {
            if (fallback == null) {
                fallback = offer;
            }
            boolean hasPrice = !offer.path("price").asString("").isBlank()
                    || !offer.path("lowPrice").asString("").isBlank();
            if (hasPrice) {
                return offer;
            }
        }
        return fallback;
    }

    private String firstNonBlank(String a, String b) {
        return !a.isBlank() ? a : b;
    }

    private String emptyToNull(String value) {
        return value.isBlank() ? null : value;
    }
}
