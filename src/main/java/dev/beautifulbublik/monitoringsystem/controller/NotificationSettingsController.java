package dev.beautifulbublik.monitoringsystem.controller;

import dev.beautifulbublik.monitoringsystem.dto.ErrorResponse;
import dev.beautifulbublik.monitoringsystem.dto.NotificationSettingsRequest;
import dev.beautifulbublik.monitoringsystem.dto.NotificationSettingsResponse;
import dev.beautifulbublik.monitoringsystem.security.AuthenticatedUser;
import dev.beautifulbublik.monitoringsystem.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings/notifications")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Notification settings", description = "The user's notification channels")
public class NotificationSettingsController {

    private final NotificationService notificationService;

    public NotificationSettingsController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @Operation(summary = "Current notification settings")
    public NotificationSettingsResponse get(@AuthenticationPrincipal AuthenticatedUser user) {
        return NotificationSettingsResponse.of(notificationService.getByUserId(user.getUserId()));
    }

    @PutMapping
    @Operation(
            summary = "Change notification channels",
            description = """
                    To enable Telegram you need a `telegramChatId`. Getting it is easy:
                    send the bot the `/start` command — it will reply with your chat ID.
                    """)
    @ApiResponse(responseCode = "200", description = "Settings saved")
    @ApiResponse(responseCode = "400", description = "Telegram enabled without a chat ID",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public NotificationSettingsResponse update(@AuthenticationPrincipal AuthenticatedUser user,
                                               @Valid @RequestBody NotificationSettingsRequest request) {
        return NotificationSettingsResponse.of(
                notificationService.update(user.getUserId(), request));
    }
}
