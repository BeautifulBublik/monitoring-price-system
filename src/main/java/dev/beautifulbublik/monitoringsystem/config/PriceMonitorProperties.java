package dev.beautifulbublik.monitoringsystem.config;

import dev.beautifulbublik.monitoringsystem.parser.shop.ShopRule;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The entire configurable part of the application. Secrets (JWT, SMTP, Telegram) are not stored
 * here as values — in {@code application.yml} they are injected from environment variables.
 */
@Getter
@ConfigurationProperties(prefix = "price-monitor")
public class PriceMonitorProperties {

    private final Parsing parsing = new Parsing();
    private final History history = new History();
    private final Selenium selenium = new Selenium();
    private final Telegram telegram = new Telegram();
    private final Mail mail = new Mail();
    private final Jwt jwt = new Jwt();

    @Setter
    private Map<String, ShopRule> shops = new LinkedHashMap<>();

    @Setter
    private ShopRule defaultRule = new ShopRule();

    @Setter
    @Getter
    public static class Parsing {

        private Duration timeout = Duration.ofSeconds(10);
        private Duration minIntervalPerDomain = Duration.ofSeconds(5);
        private Duration rateLimitMaxWait = Duration.ofSeconds(30);
        private String defaultCurrency = "UAH";
        private List<String> userAgents = new ArrayList<>();

    }

    @Setter
    @Getter
    public static class History {

        private StoreMode storeMode = StoreMode.ON_CHANGE;
        public enum StoreMode {
            ON_CHANGE,
            ALWAYS
        }
    }

    @Setter
    @Getter
    public static class Selenium {

        private String remoteUrl;
        private boolean enabled = true;
        private Duration pageLoadTimeout = Duration.ofSeconds(20);
        private Duration explicitWait = Duration.ofSeconds(10);

    }

    @Setter
    @Getter
    public static class Telegram {
        private boolean enabled = false;
        private String botToken;
        private String apiUrl = "https://api.telegram.org";
        private boolean pollingEnabled = true;

    }

    @Setter
    @Getter
    public static class Jwt {
        private String secret;
        private Duration expiration = Duration.ofHours(24);

    }
    @Setter
    @Getter
    public static class Mail {
        private boolean enabled = false;
        private String from = "no-reply@pricemonitor.local";

    }
}
