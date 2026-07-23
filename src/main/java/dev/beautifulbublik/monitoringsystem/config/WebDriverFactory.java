package dev.beautifulbublik.monitoringsystem.config;

import dev.beautifulbublik.monitoringsystem.parser.PriceParsingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

/**
 * Creates a headless Chrome for each parsing task.
 * <p>
 * WebDriver is not thread-safe, and a single long-lived instance for the whole application
 * leaks memory over time and gets stuck on a broken page. So the driver is created per parse
 * and guaranteed to be closed — we pay the startup time (~1-2 s) for isolation. Selenium is
 * only used as a fallback, so this is acceptable.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebDriverFactory {
    private final PriceMonitorProperties properties;


    public boolean isEnabled() {
        return properties.getSelenium().isEnabled();
    }

    public WebDriver create() {
        PriceMonitorProperties.Selenium config = properties.getSelenium();
        if (!config.isEnabled()) {
            throw new PriceParsingException("Selenium is disabled (price-monitor.selenium.enabled=false)");
        }

        ChromeOptions options = buildOptions();

        try {
            WebDriver driver = (config.getRemoteUrl() == null || config.getRemoteUrl().isBlank())
                    ? new ChromeDriver(options)
                    : new RemoteWebDriver(toUrl(config.getRemoteUrl()), options);

            driver.manage().timeouts().pageLoadTimeout(config.getPageLoadTimeout());
            return driver;
        } catch (RuntimeException e) {
            throw new PriceParsingException("Failed to start WebDriver: " + e.getMessage(), e);
        }
    }

    private ChromeOptions buildOptions() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments(
                "--headless=new",
                "--disable-gpu",
                "--disable-dev-shm-usage",
                "--no-sandbox",
                "--window-size=1920,1080",
                "--disable-blink-features=AutomationControlled");

        if (!properties.getParsing().getUserAgents().isEmpty()) {
            options.addArguments("--user-agent=" + properties.getParsing().getUserAgents().getFirst());
        }
        return options;
    }

    private URL toUrl(String remoteUrl) {
        try {
            return URI.create(remoteUrl).toURL();
        } catch (MalformedURLException e) {
            log.error("Malformed price-monitor.selenium.remote-url: {}", remoteUrl);
            throw new PriceParsingException("Malformed remote-url for Selenium: " + remoteUrl, e);
        }
    }
}
