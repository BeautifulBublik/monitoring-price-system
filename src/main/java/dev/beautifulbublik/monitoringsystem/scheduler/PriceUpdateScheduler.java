package dev.beautifulbublik.monitoringsystem.scheduler;

import dev.beautifulbublik.monitoringsystem.entity.TrackingStatus;
import dev.beautifulbublik.monitoringsystem.parser.PriceParsingException;
import dev.beautifulbublik.monitoringsystem.repository.ProductRepository;
import dev.beautifulbublik.monitoringsystem.service.PriceCheckService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Background sweep of active products.
 * <p>
 * The key property: <b>no single error brings down the cycle</b>. A failing shop, changed
 * markup or a timeout affect exactly one product — the rest are checked as usual.
 * <p>
 * The interval is set by the {@code price-monitor.scheduler.interval} (cron) expression in config.
 * While there are few products, {@code @Scheduled} is enough; as load grows this class is the
 * single place that will have to be replaced with a Quartz job
 * (a cluster lock and task distribution across instances are needed, which @Scheduled cannot do).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PriceUpdateScheduler {

    private final ProductRepository productRepository;
    private final PriceCheckService priceCheckService;


    @Scheduled(cron = "${price-monitor.scheduler.cron}", zone = "${price-monitor.scheduler.zone:UTC}")
    public void updateAllPrices() {
        Instant startedAt = Instant.now();
        List<ProductRef> products = loadActiveProducts();

        if (products.isEmpty()) {
            log.debug("No active products, check cycle skipped");
            return;
        }

        log.info("Starting price sweep: {} active products", products.size());

        int succeeded = 0;
        int failed = 0;
        for (ProductRef product : products) {
            try {
                priceCheckService.check(product.id(), product.url());
                succeeded++;
            } catch (PriceParsingException e) {
                failed++;
                log.warn("Failed to check product {} ({}): {}", product.id(), product.url(), e.getMessage());
            } catch (RuntimeException e) {
                failed++;
                log.error("Unexpected error while checking product {} ({})", product.id(), product.url(), e);
            }
        }

        log.info("Price sweep finished in {} s: succeeded {}, failed {}",
                Duration.between(startedAt, Instant.now()).toSeconds(), succeeded, failed);
    }

    private List<ProductRef> loadActiveProducts() {
        return productRepository.findAllForCheck(TrackingStatus.ACTIVE).stream()
                .map(product -> new ProductRef(product.getId(), product.getUrl()))
                .toList();
    }

    private record ProductRef(Long id, String url) {
    }
}
