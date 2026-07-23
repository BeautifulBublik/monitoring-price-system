package dev.beautifulbublik.monitoringsystem.notification;

import dev.beautifulbublik.monitoringsystem.config.PriceMonitorProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A bot that replies to {@code /start} with the user's chat ID — the user pastes this id
 * into their profile settings to enable Telegram notifications.
 * <p>
 * Runs in a dedicated daemon thread rather than on {@code @Scheduled}: long polling keeps
 * the HTTP connection open for up to 30 seconds and would occupy a thread from the shared pool
 * that the price checks use.
 */
@Component
public class TelegramBotPoller {

    private static final Logger log = LoggerFactory.getLogger(TelegramBotPoller.class);

    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration ERROR_BACKOFF = Duration.ofSeconds(5);

    private final TelegramClient telegramClient;
    private final PriceMonitorProperties properties;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread pollingThread;

    private long offset;

    public TelegramBotPoller(TelegramClient telegramClient, PriceMonitorProperties properties) {
        this.telegramClient = telegramClient;
        this.properties = properties;
    }

    @PostConstruct
    public void start() {
        if (!telegramClient.isConfigured() || !properties.getTelegram().isPollingEnabled()) {
            log.info("Telegram bot not started (token not configured or polling disabled)");
            return;
        }

        running.set(true);
        pollingThread = new Thread(this::pollLoop, "telegram-bot-poller");
        pollingThread.setDaemon(true);
        pollingThread.start();
        log.info("Telegram bot started, listening for the /start command");
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        if (pollingThread != null) {
            pollingThread.interrupt();
        }
    }

    private void pollLoop() {
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                JsonNode response = telegramClient.getUpdates(offset, POLL_TIMEOUT);
                handleUpdates(response);
            } catch (NotificationException e) {
                log.warn("Long polling error: {}. Retrying in {} s", e.getMessage(), ERROR_BACKOFF.toSeconds());
                sleep(ERROR_BACKOFF);
            } catch (RuntimeException e) {
                log.error("Unexpected error in the Telegram poller", e);
                sleep(ERROR_BACKOFF);
            }
        }
        log.info("Telegram bot stopped");
    }

    private void handleUpdates(JsonNode response) {
        if (response == null || !response.path("ok").asBoolean(false)) {
            return;
        }

        for (JsonNode update : response.path("result")) {
            long updateId = update.path("update_id").asLong();
            offset = Math.max(offset, updateId + 1);

            JsonNode message = update.path("message");
            String text = message.path("text").asString("");
            String chatId = message.path("chat").path("id").asString("");

            if (text.startsWith("/start") && !chatId.isBlank()) {
                replyWithChatId(chatId);
            }
        }
    }

    private void replyWithChatId(String chatId) {
        String reply = """
                👋 Hi! This is the Price Monitor bot.

                Your chat ID: <code>%s</code>

                Copy it and paste it into your profile settings
                (<b>PUT /api/settings/notifications</b>, field <code>telegramChatId</code>)
                to receive price-drop notifications here.
                """.formatted(chatId);
        try {
            telegramClient.sendMessage(chatId, reply);
            log.info("Sent chat ID to user {}", chatId);
        } catch (NotificationException e) {
            log.warn("Failed to reply to /start in chat {}: {}", chatId, e.getMessage());
        }
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
