package dev.beautifulbublik.monitoringsystem.service;

import dev.beautifulbublik.monitoringsystem.dto.NotificationSettingsRequest;
import dev.beautifulbublik.monitoringsystem.entity.NotificationSettings;
import dev.beautifulbublik.monitoringsystem.entity.User;
import dev.beautifulbublik.monitoringsystem.exception.BadRequestException;
import dev.beautifulbublik.monitoringsystem.exception.ResourceNotFoundException;
import dev.beautifulbublik.monitoringsystem.notification.NotificationChannel;
import dev.beautifulbublik.monitoringsystem.notification.NotificationException;
import dev.beautifulbublik.monitoringsystem.notification.Notifier;
import dev.beautifulbublik.monitoringsystem.notification.PriceDropNotification;
import dev.beautifulbublik.monitoringsystem.repository.NotificationSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Stores channel settings and dispatches notifications over the enabled ones.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationSettingsRepository settingsRepository;
    private final Map<NotificationChannel, Notifier> notifiers = new EnumMap<>(NotificationChannel.class);

    public NotificationService(NotificationSettingsRepository settingsRepository, List<Notifier> notifiers) {
        this.settingsRepository = settingsRepository;
        notifiers.forEach(notifier -> this.notifiers.put(notifier.channel(), notifier));
    }
    @Async
    public void send(PriceDropNotification notification, NotificationSettings settings) {
        for (NotificationChannel channel : enabledChannels(settings)) {
            Notifier notifier = notifiers.get(channel);
            if (notifier == null || !notifier.isEnabled()) {
                log.debug("Channel {} is enabled for the user but not configured in the application — skipping", channel);
                continue;
            }
            try {
                notifier.send(notification);
            } catch (NotificationException e) {
                log.warn("Channel {} failed to deliver the notification for product '{}': {}",
                        channel, notification.productTitle(), e.getMessage());
            }
        }
    }

    private List<NotificationChannel> enabledChannels(NotificationSettings settings) {
        List<NotificationChannel> channels = new ArrayList<>(2);
        if (settings.isEmailEnabled()) {
            channels.add(NotificationChannel.EMAIL);
        }
        if (settings.isTelegramEnabled()) {
            channels.add(NotificationChannel.TELEGRAM);
        }
        return channels;
    }

    @Transactional(readOnly = true)
    public NotificationSettings getByUserId(Long userId) {
        return settingsRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Notification settings for user " + userId + " not found"));
    }

    @Transactional
    public NotificationSettings update(Long userId, NotificationSettingsRequest request) {
        if (Boolean.TRUE.equals(request.telegramEnabled())
                && (request.telegramChatId() == null || request.telegramChatId().isBlank())) {
            throw new BadRequestException(
                    "To enable Telegram, provide telegramChatId — the bot sends it in response to the /start command");
        }

        NotificationSettings settings = settingsRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Notification settings for user " + userId + " not found"));

        settings.setEmailEnabled(Boolean.TRUE.equals(request.emailEnabled()));
        settings.setTelegramEnabled(Boolean.TRUE.equals(request.telegramEnabled()));
        settings.setTelegramChatId(request.telegramChatId());

        log.info("Updated notification settings for user {}: email={}, telegram={}",
                userId, settings.isEmailEnabled(), settings.isTelegramEnabled());
        return settingsRepository.save(settings);
    }

    @Transactional
    public void createDefaults(User user) {
        settingsRepository.save(new NotificationSettings(user));
    }
}
