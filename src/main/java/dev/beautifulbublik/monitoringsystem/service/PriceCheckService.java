package dev.beautifulbublik.monitoringsystem.service;

import dev.beautifulbublik.monitoringsystem.config.PriceMonitorProperties;
import dev.beautifulbublik.monitoringsystem.config.PriceMonitorProperties.History.StoreMode;
import dev.beautifulbublik.monitoringsystem.entity.NotificationSettings;
import dev.beautifulbublik.monitoringsystem.entity.PriceHistory;
import dev.beautifulbublik.monitoringsystem.entity.Product;
import dev.beautifulbublik.monitoringsystem.exception.ResourceNotFoundException;
import dev.beautifulbublik.monitoringsystem.notification.PriceDropNotification;
import dev.beautifulbublik.monitoringsystem.parser.ParsedPrice;
import dev.beautifulbublik.monitoringsystem.parser.PriceParsingService;
import dev.beautifulbublik.monitoringsystem.repository.NotificationSettingsRepository;
import dev.beautifulbublik.monitoringsystem.repository.PriceHistoryRepository;
import dev.beautifulbublik.monitoringsystem.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PriceCheckService {

    private final ProductRepository productRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final NotificationSettingsRepository settingsRepository;
    private final PriceParsingService priceParsingService;
    private final ThresholdEvaluator thresholdEvaluator;
    private final NotificationService notificationService;
    private final TransactionTemplate transactionTemplate;
    private final PriceMonitorProperties properties;

    public void check(Long productId, String url) {
        ParsedPrice parsed = priceParsingService.parse(url);

        CheckOutcome outcome = transactionTemplate.execute(status -> record(productId, parsed));

        if (outcome != null && outcome.notification() != null) {
            notificationService.send(outcome.notification(), outcome.settings());
        }
    }

    private CheckOutcome record(Long productId, ParsedPrice parsed) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product " + productId + " not found"));

        BigDecimal previousPrice = priceHistoryRepository
                .findFirstByProductIdOrderByCheckedAtDesc(productId)
                .map(PriceHistory::getPrice)
                .orElse(null);
        BigDecimal minPrice = priceHistoryRepository.findMinPrice(productId).orElse(null);

        Instant now = Instant.now();
        if (shouldStore(previousPrice, parsed.price())) {
            priceHistoryRepository.save(new PriceHistory(product, parsed.price(), parsed.currency(), now));
        }
        product.setLastCheckedAt(now);

        if (!thresholdEvaluator.shouldNotify(product, previousPrice, minPrice, parsed.price())) {
            return CheckOutcome.silent();
        }

        product.setLastNotifiedPrice(parsed.price());

        Optional<NotificationSettings> settings = settingsRepository.findByUserId(product.getUser().getId());
        if (settings.isEmpty()) {
            log.warn("User {} has no notification settings — notification skipped",
                    product.getUser().getId());
            return CheckOutcome.silent();
        }

        log.info("Price of product {} ('{}') dropped: {} -> {} {}",
                productId, product.getTitle(), previousPrice, parsed.price(), parsed.currency());

        PriceDropNotification notification = new PriceDropNotification(
                product.getUser().getEmail(),
                settings.get().getTelegramChatId(),
                product.getTitle(),
                product.getUrl(),
                product.getShopName(),
                previousPrice,
                parsed.price(),
                parsed.currency());

        return new CheckOutcome(notification, settings.get());
    }

    private boolean shouldStore(BigDecimal previousPrice, BigDecimal newPrice) {
        if (properties.getHistory().getStoreMode() == StoreMode.ALWAYS) {
            return true;
        }
        return previousPrice == null || previousPrice.compareTo(newPrice) != 0;
    }

    private record CheckOutcome(PriceDropNotification notification, NotificationSettings settings) {

        static CheckOutcome silent() {
            return new CheckOutcome(null, null);
        }
    }
}
