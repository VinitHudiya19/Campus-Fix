package com.campusfix.notification.channel;

import com.campusfix.notification.NotificationType;
import com.campusfix.request.ServiceRequest;
import com.campusfix.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.annotation.Order;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Sends the same notification by email.
 *
 * <p>Registered <strong>only when {@code spring.mail.host} is set</strong>. With
 * no mail server configured this bean does not exist, the application starts
 * exactly as before, and nothing anywhere needs a null check — which is the
 * point of making a channel a bean rather than an {@code if} inside a service.
 *
 * <p>Ordered after the in-app channel so the database record is written first.
 * If a mail server is slow or down, the recipient has still been told.
 */
@Component
@Order(2)
// Checks for a non-empty host rather than merely a present one. A blank value
// counts as present to @ConditionalOnProperty, which would register this
// channel against a mail server that does not exist.
@ConditionalOnExpression("!'${spring.mail.host:}'.isEmpty()")
public class EmailChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(EmailChannel.class);

    private final JavaMailSender mailSender;
    private final String from;
    private final String baseUrl;

    public EmailChannel(JavaMailSender mailSender,
                        @Value("${campusfix.mail.from:campusfix@localhost}") String from,
                        @Value("${campusfix.base-url:http://localhost:8080}") String baseUrl) {
        this.mailSender = mailSender;
        this.from = from;
        this.baseUrl = baseUrl;
        log.info("Email notifications are on, sending as {}", from);
    }

    @Override
    public void deliver(User recipient, ServiceRequest request, NotificationType type, String message) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(from);
        mail.setTo(recipient.getEmail());
        mail.setSubject("[%s] %s".formatted(request.getRequestNumber(), type.getDisplayName()));
        mail.setText(body(recipient, request, message));

        try {
            mailSender.send(mail);
        } catch (MailException e) {
            // Swallowed on purpose, and logged loudly. The in-app notification
            // has already been written, so the recipient has been told; failing
            // here would abandon the remaining channels for no benefit.
            log.error("Could not email {} about request {}", recipient.getEmail(),
                    request.getRequestNumber(), e);
        }
    }

    /** Plain text. An HTML template would need styling for a dozen mail clients. */
    private String body(User recipient, ServiceRequest request, String message) {
        return """
                Hello %s,

                %s

                Request:  %s
                Title:    %s
                Status:   %s

                You can see it here:
                %s/request-detail.html?id=%d

                --
                CampusFix
                This is an automated message; replies are not read.
                """.formatted(
                recipient.getFullName(),
                message,
                request.getRequestNumber(),
                request.getTitle(),
                request.getStatus().getDisplayName(),
                baseUrl,
                request.getId());
    }

    @Override
    public String describe() {
        return "email";
    }
}
