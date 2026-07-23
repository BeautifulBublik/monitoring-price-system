package dev.beautifulbublik.monitoringsystem.notification;

import dev.beautifulbublik.monitoringsystem.config.PriceMonitorProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.nio.charset.StandardCharsets;

/**
 * HTML email from the {@code templates/email/price-drop.html} template.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmailNotifier implements Notifier {


    private static final String TEMPLATE = "email/price-drop";

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final PriceMonitorProperties properties;

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public boolean isEnabled() {
        return properties.getMail().isEnabled();
    }

    @Override
    public void send(PriceDropNotification notification) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message, MimeMessageHelper.MULTIPART_MODE_NO, StandardCharsets.UTF_8.name());

            helper.setFrom(properties.getMail().getFrom());
            helper.setTo(notification.recipientEmail());
            helper.setSubject(buildSubject(notification));
            helper.setText(renderBody(notification), true);

            mailSender.send(message);
            log.info("Price-drop email sent to {} (product '{}')",
                    notification.recipientEmail(), notification.productTitle());
        } catch (MessagingException | MailException e) {
            throw new NotificationException(
                    "Failed to send email to " + notification.recipientEmail(), e);
        }
    }

    private String buildSubject(PriceDropNotification notification) {
        return "Price dropped by %s%%: %s".formatted(
                notification.percentDrop().toPlainString(),
                notification.productTitle());
    }

    private String renderBody(PriceDropNotification notification) {
        Context context = new Context();
        context.setVariable("title", notification.productTitle());
        context.setVariable("url", notification.productUrl());
        context.setVariable("shop", notification.shopName());
        context.setVariable("oldPrice", notification.oldPrice().toPlainString());
        context.setVariable("newPrice", notification.newPrice().toPlainString());
        context.setVariable("currency", notification.currency());
        context.setVariable("absoluteDrop", notification.absoluteDrop().toPlainString());
        context.setVariable("percentDrop", notification.percentDrop().toPlainString());
        return templateEngine.process(TEMPLATE, context);
    }
}
