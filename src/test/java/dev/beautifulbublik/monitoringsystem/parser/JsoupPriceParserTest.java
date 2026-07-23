package dev.beautifulbublik.monitoringsystem.parser;

import dev.beautifulbublik.monitoringsystem.config.PriceMonitorProperties;
import dev.beautifulbublik.monitoringsystem.parser.shop.ShopRule;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Parsing is tested on a "mocked" HTTP response: instead of a real request we feed ready HTML
 * straight into {@code extract}. The tests do not go to the network, so they are fast and do not
 * depend on whether the shop is alive.
 */
class JsoupPriceParserTest {

    private static final String URL = "https://example-shop.com/product/42";

    private JsoupPriceParser parser;
    private ShopRule rule;

    @BeforeEach
    void setUp() {
        PriceMonitorProperties properties = new PriceMonitorProperties();
        properties.getParsing().setDefaultCurrency("RUB");
        parser = new JsoupPriceParser(properties, new JsonLdPriceExtractor());

        rule = new ShopRule();
        rule.setPriceSelector(".price");
        rule.setTitleSelector("h1");
    }

    private ParsedPrice parse(String html) {
        Document document = Jsoup.parse(html, URL);
        return parser.extract(document, rule, URL);
    }

    @Test
    @DisplayName("Extracts name, price and currency from an ordinary page")
    void parsesStaticPage() {
        ParsedPrice parsed = parse("""
                <html><body>
                  <h1>Example Pro 14 Laptop</h1>
                  <div class="price">89 990 ₽</div>
                </body></html>
                """);

        assertThat(parsed.title()).isEqualTo("Example Pro 14 Laptop");
        assertThat(parsed.price()).isEqualByComparingTo(new BigDecimal("89990.00"));
        assertThat(parsed.currency()).isEqualTo("RUB");
    }

    @Test
    @DisplayName("Reads the price from the content attribute — this is how schema.org microdata exposes it")
    void parsesMicrodataAttribute() {
        rule.setPriceSelector("[itemprop=price]");
        rule.setCurrencySelector("[itemprop=priceCurrency]");

        ParsedPrice parsed = parse("""
                <html><body>
                  <h1>Example S Smartphone</h1>
                  <meta itemprop="price" content="54990.00">
                  <meta itemprop="priceCurrency" content="USD">
                </body></html>
                """);

        assertThat(parsed.price()).isEqualByComparingTo(new BigDecimal("54990.00"));
        assertThat(parsed.currency()).isEqualTo("USD");
    }

    @Test
    @DisplayName("Currency from the shop config when the page has neither symbol nor code")
    void fallsBackToConfiguredCurrency() {
        rule.setCurrency("EUR");

        ParsedPrice parsed = parse("<html><body><h1>T</h1><div class='price'>1299</div></body></html>");

        assertThat(parsed.currency()).isEqualTo("EUR");
    }

    @Test
    @DisplayName("Changed markup: neither the CSS selector nor JSON-LD matched -> a clear error, not an NPE")
    void failsWhenPriceSelectorMisses() {
        assertThatThrownBy(() -> parse("<html><body><h1>Product</h1><div class='new-price'>100 ₽</div></body></html>"))
                .isInstanceOf(PriceParsingException.class)
                .hasMessageContaining(".price");
    }

    @Test
    @DisplayName("The CSS selector is absent on the page but schema.org JSON-LD is present -> the price is taken from it")
    void fallsBackToJsonLd() {
        rule.setPriceSelector("[itemprop=price]");

        ParsedPrice parsed = parse("""
                <html><head>
                  <script type="application/ld+json">
                  {
                    "@context": "https://schema.org",
                    "@type": "Product",
                    "name": "Електрична зубна щітка PHILIPS Sonicare",
                    "offers": { "@type": "Offer", "price": 14999, "priceCurrency": "UAH" }
                  }
                  </script>
                </head><body><h1>PHILIPS Sonicare</h1></body></html>
                """);

        assertThat(parsed.title()).isEqualTo("Електрична зубна щітка PHILIPS Sonicare");
        assertThat(parsed.price()).isEqualByComparingTo(new BigDecimal("14999.00"));
        assertThat(parsed.currency()).isEqualTo("UAH");
    }

    @Test
    @DisplayName("Cloudflare stub \"Just a moment…\" -> a clear anti-bot-protection error")
    void detectsCloudflareChallenge() {
        rule.setPriceSelector("[itemprop=price]");

        assertThatThrownBy(() -> parse("""
                <html><head><title>Just a moment...</title></head>
                <body><div class="cf-chl-widget"></div></body></html>
                """))
                .isInstanceOf(PriceParsingException.class)
                .hasMessageContaining("Cloudflare");
    }

    @Test
    @DisplayName("Anti-bot stub instead of the page: no digits -> parsing error")
    void failsOnAntiBotStub() {
        assertThatThrownBy(() -> parse("""
                <html><body>
                  <h1>Security check</h1>
                  <div class="price">Confirm that you are not a robot</div>
                </body></html>
                """))
                .isInstanceOf(PriceParsingException.class);
    }

    @Test
    @DisplayName("The name was not found — we don't fail: the price matters more, the product is still tracked")
    void survivesMissingTitle() {
        rule.setTitleSelector("h1.does-not-exist");

        ParsedPrice parsed = parse("""
                <html><head><title>Fallback title</title></head>
                <body><div class="price">500 ₽</div></body></html>
                """);

        assertThat(parsed.price()).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(parsed.title()).isEqualTo("Fallback title");
    }

    @Test
    @DisplayName("An empty price-selector is allowed if JSON-LD provides the price (the default-rule case)")
    void worksWithoutPriceSelectorViaJsonLd() {
        rule.setPriceSelector(null);

        ParsedPrice parsed = parse("""
                <html><head>
                  <script type="application/ld+json">
                  {"@type":"Product","name":"Product","offers":{"@type":"Offer","price":"1 299","priceCurrency":"UAH"}}
                  </script>
                </head><body></body></html>
                """);

        assertThat(parsed.price()).isEqualByComparingTo(new BigDecimal("1299.00"));
        assertThat(parsed.currency()).isEqualTo("UAH");
    }

    @Test
    @DisplayName("Neither CSS selector nor JSON-LD — we report that both approaches were tried")
    void failsWhenNeitherCssNorJsonLd() {
        rule.setPriceSelector(null);

        assertThatThrownBy(() -> parse("<html><body><div class='price'>100</div></body></html>"))
                .isInstanceOf(PriceParsingException.class)
                .hasMessageContaining("JSON-LD");
    }
}
