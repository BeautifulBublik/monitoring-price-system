package dev.beautifulbublik.monitoringsystem.parser;

import dev.beautifulbublik.monitoringsystem.parser.shop.RenderMode;
import dev.beautifulbublik.monitoringsystem.parser.shop.ShopRule;
import dev.beautifulbublik.monitoringsystem.parser.shop.ShopRuleRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Entry point into parsing: picks the shop strategy, honors the rate limit
 * and decides whether a Selenium fallback is needed.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PriceParsingService {


    private final JsoupPriceParser jsoupParser;
    private final SeleniumPriceParser seleniumParser;
    private final ShopRuleRegistry shopRuleRegistry;
    private final DomainRateLimiter rateLimiter;

    public ParsedPrice parse(String url) {
        ShopRule rule = shopRuleRegistry.resolve(url);
        String domain = shopRuleRegistry.extractHost(url);

        if (!rateLimiter.acquire(domain)) {
            throw new PriceParsingException("Request limit for domain " + domain + " exceeded, will retry later");
        }

        return switch (rule.getRenderMode()) {
            case JSOUP_ONLY -> jsoupParser.parse(url, rule);
            case SELENIUM_ONLY -> seleniumParser.parse(url, rule);
            case AUTO -> parseWithFallback(url, rule);
        };
    }

    private ParsedPrice parseWithFallback(String url, ShopRule rule) {
        try {
            return jsoupParser.parse(url, rule);
        } catch (PriceParsingException jsoupFailure) {
            if (!seleniumParser.isEnabled()) {
                throw jsoupFailure;
            }
            log.info("Jsoup could not handle {} ({}), trying Selenium", url, jsoupFailure.getMessage());
            try {
                return seleniumParser.parse(url, rule);
            } catch (PriceParsingException seleniumFailure) {
                seleniumFailure.addSuppressed(jsoupFailure);
                throw seleniumFailure;
            }
        }
    }
}
