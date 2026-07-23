package dev.beautifulbublik.monitoringsystem.parser;

import dev.beautifulbublik.monitoringsystem.config.PriceMonitorProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prevents hammering a single domain more often than once per {@code min-interval-per-domain}.
 * <p>
 * The implementation is deliberately simple: for each domain we store the earliest time the next
 * request may be made. The thread that takes a slot moves this marker forward before it even sleeps,
 * so competing threads queue up instead of all waking at once.
 */
@Component
@Slf4j
public class DomainRateLimiter {
    private final Map<String, Long> nextAllowedAtMillis = new ConcurrentHashMap<>();
    private final Duration minInterval;
    private final Duration maxWait;

    public DomainRateLimiter(PriceMonitorProperties properties) {
        this.minInterval = properties.getParsing().getMinIntervalPerDomain();
        this.maxWait = properties.getParsing().getRateLimitMaxWait();
    }

    public boolean acquire(String domain) {
        long intervalMillis = minInterval.toMillis();
        long now = System.currentTimeMillis();

        long myTurn = nextAllowedAtMillis.merge(
                domain,
                now + intervalMillis,
                (existing, candidate) -> Math.max(existing, now) + intervalMillis);

        long waitUntil = myTurn - intervalMillis;
        long waitMillis = waitUntil - now;

        if (waitMillis <= 0) {
            return true;
        }
        if (waitMillis > maxWait.toMillis()) {
            log.debug("Domain {} is overloaded: would wait {} ms, limit {} ms — skipping this cycle",
                    domain, waitMillis, maxWait.toMillis());
            return false;
        }

        try {
            log.debug("Rate limit for domain {}: waiting {} ms", domain, waitMillis);
            Thread.sleep(waitMillis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
