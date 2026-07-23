package dev.beautifulbublik.monitoringsystem.parser;

import dev.beautifulbublik.monitoringsystem.config.PriceMonitorProperties;
import dev.beautifulbublik.monitoringsystem.config.WebDriverFactory;
import dev.beautifulbublik.monitoringsystem.parser.shop.ShopRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Fallback for shops that render the price via JavaScript.
 * <p>
 * Waits for the target selector to appear, grabs the ready DOM and hands it to the same
 * {@link JsoupPriceParser#extract}: the selector and number-parsing logic is shared, only the
 * way the HTML is obtained differs.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SeleniumPriceParser implements PriceParser {

    private final WebDriverFactory webDriverFactory;
    private final JsoupPriceParser documentExtractor;
    private final PriceMonitorProperties properties;


    @Override
    public String name() {
        return "selenium";
    }

    public boolean isEnabled() {
        return webDriverFactory.isEnabled();
    }

    @Override
    public ParsedPrice parse(String url, ShopRule rule) {
        WebDriver driver = webDriverFactory.create();
        try {
            driver.get(url);
            waitForPrice(driver, rule, url);

            Document document = Jsoup.parse(driver.getPageSource(), url);
            return documentExtractor.extract(document, rule, url);
        } catch (WebDriverException e) {
            throw new PriceParsingException("Selenium failed to load " + url + ": " + e.getMessage(), e);
        } finally {
            quitQuietly(driver);
        }
    }

    private void waitForPrice(WebDriver driver, ShopRule rule, String url) {
        String selector = rule.effectiveWaitSelector();
        if (selector == null || selector.isBlank()) {
            return;
        }
        try {
            new WebDriverWait(driver, properties.getSelenium().getExplicitWait())
                    .until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(selector)));
        } catch (TimeoutException e) {
            log.warn("Selenium did not find '{}' on {} within {} — trying to parse whatever loaded",
                    selector, url, properties.getSelenium().getExplicitWait());
        }
    }

    private void quitQuietly(WebDriver driver) {
        try {
            driver.quit();
        } catch (WebDriverException e) {
            log.warn("Failed to close WebDriver cleanly: {}", e.getMessage());
        }
    }
}
