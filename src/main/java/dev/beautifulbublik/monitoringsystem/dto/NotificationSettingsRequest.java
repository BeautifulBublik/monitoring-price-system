package dev.beautifulbublik.monitoringsystem.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@Schema(description = "Notification channel settings")
public record NotificationSettingsRequest(

        @Schema(example = "true")
        @NotNull(message = "emailEnabled is required")
        Boolean emailEnabled,

        @Schema(example = "true")
        @NotNull(message = "telegramEnabled is required")
        Boolean telegramEnabled,

        @Schema(description = "Chat ID from the bot's reply to the /start command. Required if telegramEnabled = true.",
                example = "123456789")
        @Pattern(regexp = "^-?\\d{1,20}$", message = "telegramChatId must be a number")
        String telegramChatId
) {
}
