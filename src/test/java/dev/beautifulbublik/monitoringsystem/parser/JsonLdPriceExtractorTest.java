package dev.beautifulbublik.monitoringsystem.parser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Extracting the price from schema.org JSON-LD is tested on inline HTML, without the network.
 * The markup shapes are taken from what shops actually serve: offer as an object,
 * an array of offers, a @graph wrapper, AggregateOffer, type as an array, broken blocks.
 */
class JsonLdPriceExtractorTest {

    private final JsonLdPriceExtractor extractor = new JsonLdPriceExtractor();

    private Optional<JsonLdPriceExtractor.JsonLdPrice> extract(String html) {
        Document document = Jsoup.parse(html, "https://shop.example/product");
        return extractor.extract(document);
    }

    @Test
    @DisplayName("Product with an offers object: name, price and currency")
    void parsesSingleOffer() {
        var result = extract("""
                <script type="application/ld+json">
                {
                  "@context": "https://schema.org",
                  "@type": "Product",
                  "name": "PHILIPS toothbrush",
                  "offers": { "@type": "Offer", "price": 14999, "priceCurrency": "UAH" }
                }
                </script>
                """);

        assertThat(result).isPresent();
        assertThat(result.get().name()).isEqualTo("PHILIPS toothbrush");
        assertThat(result.get().price()).isEqualByComparingTo(new BigDecimal("14999.00"));
        assertThat(result.get().currency()).isEqualTo("UAH");
    }

    @Test
    @DisplayName("offers as an array — the first offer with a price is taken")
    void parsesOffersArray() {
        var result = extract("""
                <script type="application/ld+json">
                {
                  "@type": "Product",
                  "name": "Product",
                  "offers": [
                    { "@type": "Offer", "priceCurrency": "UAH" },
                    { "@type": "Offer", "price": 250, "priceCurrency": "UAH" }
                  ]
                }
                </script>
                """);

        assertThat(result).isPresent();
        assertThat(result.get().price()).isEqualByComparingTo(new BigDecimal("250.00"));
    }

    @Test
    @DisplayName("A Product inside @graph is found too")
    void parsesGraphWrapper() {
        var result = extract("""
                <script type="application/ld+json">
                {
                  "@context": "https://schema.org",
                  "@graph": [
                    { "@type": "BreadcrumbList" },
                    { "@type": "Product", "name": "In the graph",
                      "offers": { "@type": "Offer", "price": 99, "priceCurrency": "UAH" } }
                  ]
                }
                </script>
                """);

        assertThat(result).isPresent();
        assertThat(result.get().name()).isEqualTo("In the graph");
        assertThat(result.get().price()).isEqualByComparingTo(new BigDecimal("99.00"));
    }

    @Test
    @DisplayName("AggregateOffer — lowPrice is taken")
    void parsesAggregateOfferLowPrice() {
        var result = extract("""
                <script type="application/ld+json">
                {
                  "@type": "Product",
                  "name": "Range",
                  "offers": { "@type": "AggregateOffer", "lowPrice": 1200, "highPrice": 1800, "priceCurrency": "UAH" }
                }
                </script>
                """);

        assertThat(result).isPresent();
        assertThat(result.get().price()).isEqualByComparingTo(new BigDecimal("1200.00"));
    }

    @Test
    @DisplayName("@type as an array [\"Product\", ...] is recognized")
    void parsesTypeAsArray() {
        var result = extract("""
                <script type="application/ld+json">
                {
                  "@type": ["Product", "IndividualProduct"],
                  "name": "Multi-type",
                  "offers": { "@type": "Offer", "price": 500, "priceCurrency": "UAH" }
                }
                </script>
                """);

        assertThat(result).isPresent();
        assertThat(result.get().price()).isEqualByComparingTo(new BigDecimal("500.00"));
    }

    @Test
    @DisplayName("Price as a string with a space thousands separator")
    void parsesStringPrice() {
        var result = extract("""
                <script type="application/ld+json">
                {"@type":"Product","name":"String","offers":{"@type":"Offer","price":"14 999","priceCurrency":"UAH"}}
                </script>
                """);

        assertThat(result).isPresent();
        assertThat(result.get().price()).isEqualByComparingTo(new BigDecimal("14999.00"));
    }

    @Test
    @DisplayName("No JSON-LD on the page -> Optional.empty()")
    void emptyWhenNoJsonLd() {
        var result = extract("<html><body><div class='price'>100 UAH</div></body></html>");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("A broken block is skipped, the price is taken from the adjacent valid one")
    void skipsBrokenBlockUsesValidOne() {
        var result = extract("""
                <script type="application/ld+json">{ this is not json,, }</script>
                <script type="application/ld+json">
                {"@type":"Product","name":"Valid","offers":{"@type":"Offer","price":777,"priceCurrency":"UAH"}}
                </script>
                """);

        assertThat(result).isPresent();
        assertThat(result.get().price()).isEqualByComparingTo(new BigDecimal("777.00"));
    }

    @Test
    @DisplayName("No currency in the markup -> currency == null (the caller substitutes one)")
    void currencyNullWhenAbsent() {
        var result = extract("""
                <script type="application/ld+json">
                {"@type":"Product","name":"No currency","offers":{"@type":"Offer","price":300}}
                </script>
                """);

        assertThat(result).isPresent();
        assertThat(result.get().currency()).isNull();
    }
}
