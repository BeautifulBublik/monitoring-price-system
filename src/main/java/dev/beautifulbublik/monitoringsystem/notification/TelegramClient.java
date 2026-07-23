package dev.beautifulbublik.monitoringsystem.notification;

import dev.beautifulbublik.monitoringsystem.config.PriceMonitorProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Thin wrapper over the Telegram Bot API on top of {@link RestClient}.
 * <p>
 * The {@code telegrambots} library is deliberately not used here: we need exactly two
 * methods ({@code sendMessage} and {@code getUpdates}), and in exchange we would have to drag in
 * transitive dependencies and an extra abstraction layer. Two HTTP calls read more simply.
 */
@Component
public class TelegramClient {

    private final RestClient restClient;
    private final PriceMonitorProperties.Telegram config;

    public TelegramClient(PriceMonitorProperties properties) {
        this.config = properties.getTelegram();
        this.restClient = RestClient.create();
    }

    public boolean isConfigured() {
        return config.isEnabled()
                && config.getBotToken() != null
                && !config.getBotToken().isBlank();
    }

    public void sendMessage(String chatId, String html) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("chat_id", chatId);
        body.put("text", html);
        body.put("parse_mode", "HTML");
        body.put("disable_web_page_preview", false);

        try {
            restClient.post()
                    .uri(methodUrl("sendMessage"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new NotificationException("Telegram rejected sendMessage for chat " + chatId, e);
        }
    }

    public JsonNode getUpdates(long offset, Duration timeout) {
        try {
            return restClient.get()
                    .uri(methodUrl("getUpdates") + "?offset=" + offset
                            + "&timeout=" + timeout.toSeconds()
                            + "&allowed_updates=[\"message\"]")
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            throw new NotificationException("Failed to fetch Telegram updates", e);
        }
    }

    private String methodUrl(String method) {
        return config.getApiUrl() + "/bot" + config.getBotToken() + "/" + method;
    }
}
