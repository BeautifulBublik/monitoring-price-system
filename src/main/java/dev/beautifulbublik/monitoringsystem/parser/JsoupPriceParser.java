package dev.beautifulbublik.monitoringsystem.parser;

import dev.beautifulbublik.monitoringsystem.config.PriceMonitorProperties;
import dev.beautifulbublik.monitoringsystem.parser.shop.ShopRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Primary parser: a single HTTP request and parsing of static HTML.
 * Covers most shops and is an order of magnitude cheaper than Selenium.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JsoupPriceParser implements PriceParser {


    private static final String FALLBACK_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    private final PriceMonitorProperties properties;
    private final JsonLdPriceExtractor jsonLdPriceExtractor;


    @Override
    public String name() {
        return "jsoup";
    }

    @Override
    public ParsedPrice parse(String url, ShopRule rule) {
        Document document = fetch(url);
        return extract(document, rule, url);
    }

    private Document fetch(String url) {
        try {
            Connection connection = Jsoup.connect(url)
                    .userAgent(pickUserAgent())
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
                    .header("Referer", originOf(url))
                    .header("Upgrade-Insecure-Requests", "1")
                    .header("Sec-Fetch-Dest", "document")
                    .header("Sec-Fetch-Mode", "navigate")
                    .header("Sec-Fetch-Site", "same-origin")
                    .header("Sec-Fetch-User", "?1")
                    .timeout((int) properties.getParsing().getTimeout().toMillis())
                    .followRedirects(true);
            return connection.get();
        } catch (HttpStatusException e) {
            throw new PriceParsingException(
                    "HTTP " + e.getStatusCode() + " from " + url, e);
        } catch (SocketTimeoutException e) {
            throw new PriceParsingException("Timeout while loading " + url, e);
        } catch (IOException e) {
            throw new PriceParsingException("Site unreachable: " + url, e);
        }
    }

    ParsedPrice extract(Document document, ShopRule rule, String url) {
        String selector = rule.getPriceSelector();
        if (selector != null && !selector.isBlank()) {
            Element priceElement = document.selectFirst(selector);
            if (priceElement != null) {
                String priceText = textOf(priceElement);
                BigDecimal price = PriceTextParser.parsePrice(priceText);
                String currency = resolveCurrency(document, rule, priceText);
                String title = resolveTitle(document, rule);
                log.debug("Jsoup (CSS) parsed {}: title='{}', price={} {}", url, title, price, currency);
                return new ParsedPrice(title, price, currency);
            }
        }

        Optional<JsonLdPriceExtractor.JsonLdPrice> jsonLd = jsonLdPriceExtractor.extract(document);
        if (jsonLd.isPresent()) {
            JsonLdPriceExtractor.JsonLdPrice data = jsonLd.get();
            String currency = data.currency() != null ? data.currency() : fallbackCurrency(rule);
            String title = data.name() != null ? data.name() : resolveTitle(document, rule);
            log.debug("Jsoup (JSON-LD) parsed {}: title='{}', price={} {}", url, title, data.price(), currency);
            return new ParsedPrice(title, data.price(), currency);
        }

        if (looksLikeAntiBotChallenge(document)) {
            throw new PriceParsingException("Page behind anti-bot protection (Cloudflare): " + url);
        }

        String selectorInfo = (selector == null || selector.isBlank()) ? "not set" : "'" + selector + "'";
        throw new PriceParsingException(
                "Price not found on " + url + " (CSS selector " + selectorInfo + " and schema.org JSON-LD yielded nothing)");
    }

    private boolean looksLikeAntiBotChallenge(Document document) {
        String title = document.title().toLowerCase();
        if (title.startsWith("just a moment") || title.contains("attention required")) {
            return true;
        }
        String html = document.html();
        return html.contains("challenge-platform") || html.contains("cf-chl") || html.contains("__cf_chl");
    }

    private String fallbackCurrency(ShopRule rule) {
        return rule.getCurrency() != null ? rule.getCurrency() : properties.getParsing().getDefaultCurrency();
    }

    private String originOf(String url) {
        try {
            java.net.URI uri = new java.net.URI(url);
            return uri.getScheme() + "://" + uri.getHost() + "/";
        } catch (java.net.URISyntaxException e) {
            return url;
        }
    }

    private String resolveCurrency(Document document, ShopRule rule, String priceText) {
        String fallback = rule.getCurrency() != null
                ? rule.getCurrency()
                : properties.getParsing().getDefaultCurrency();

        if (rule.getCurrencySelector() != null && !rule.getCurrencySelector().isBlank()) {
            Element element = document.selectFirst(rule.getCurrencySelector());
            if (element != null) {
                return PriceTextParser.detectCurrency(textOf(element), fallback);
            }
        }
        return PriceTextParser.detectCurrency(priceText, fallback);
    }

    private String resolveTitle(Document document, ShopRule rule) {
        if (rule.getTitleSelector() != null && !rule.getTitleSelector().isBlank()) {
            Element element = document.selectFirst(rule.getTitleSelector());
            if (element != null && !textOf(element).isBlank()) {
                return textOf(element);
            }
        }
        String title = document.title();
        return title.isBlank() ? null : title;
    }

    private String textOf(Element element) {
        for (String attribute : List.of("content", "data-price", "value")) {
            String value = element.attr(attribute);
            if (!value.isBlank()) {
                return value;
            }
        }
        return element.text();
    }

    private String pickUserAgent() {
        List<String> agents = properties.getParsing().getUserAgents();
        if (agents == null || agents.isEmpty()) {
            return FALLBACK_USER_AGENT;
        }
        return agents.get(ThreadLocalRandom.current().nextInt(agents.size()));
    }
}
