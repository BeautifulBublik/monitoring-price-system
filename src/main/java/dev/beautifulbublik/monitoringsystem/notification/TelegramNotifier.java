package dev.beautifulbublik.monitoringsystem.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class TelegramNotifier implements Notifier {

    private final TelegramClient telegramClient;


    @Override
    public NotificationChannel channel() {
        return NotificationChannel.TELEGRAM;
    }

    @Override
    public boolean isEnabled() {
        return telegramClient.isConfigured();
    }

    @Override
    public void send(PriceDropNotification notification) {
        if (notification.telegramChatId() == null || notification.telegramChatId().isBlank()) {
            throw new NotificationException("The user has Telegram enabled but no chat ID set");
        }
        telegramClient.sendMessage(notification.telegramChatId(), buildMessage(notification));
        log.info("Telegram notification sent to chat {} (product '{}')",
                notification.telegramChatId(), notification.productTitle());
    }
    private String buildMessage(PriceDropNotification notification) {
        return """
                🎉 <b>Price dropped</b>

                <b>%s</b>
                <i>%s</i>

                Was: <s>%s %s</s>
                Now: <b>%s %s</b>
                You save: %s %s (%s%%)

                <a href="%s">Open product</a>
                """.formatted(
                escape(notification.productTitle()),
                escape(notification.shopName()),
                notification.oldPrice().toPlainString(), notification.currency(),
                notification.newPrice().toPlainString(), notification.currency(),
                notification.absoluteDrop().toPlainString(), notification.currency(),
                notification.percentDrop().toPlainString(),
                notification.productUrl());
    }

    private String escape(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
