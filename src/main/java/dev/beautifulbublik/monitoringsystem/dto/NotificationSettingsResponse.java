package dev.beautifulbublik.monitoringsystem.dto;

import dev.beautifulbublik.monitoringsystem.entity.NotificationSettings;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "The user's current notification settings")
public record NotificationSettingsResponse(

        @Schema(example = "true")
        boolean emailEnabled,

        @Schema(example = "true")
        boolean telegramEnabled,

        @Schema(example = "123456789")
        String telegramChatId
) {

    public static NotificationSettingsResponse of(NotificationSettings settings) {
        return new NotificationSettingsResponse(
                settings.isEmailEnabled(),
                settings.isTelegramEnabled(),
                settings.getTelegramChatId());
    }
}
