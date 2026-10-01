package com.insurance.notification.consumer;

import com.insurance.notification.service.NotificationEmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import com.insurance.monolith.events.DomainEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component("notificationAuthEventConsumer")
@RequiredArgsConstructor
public class AuthEventConsumer {

    private final NotificationEmailService emailService;

    @Value("${app.frontend-url}") private String frontendUrl;
    @Value("${app.company-name}") private String companyName;

    @Async
    @EventListener(condition = "#domainEvent.topic() == 'auth.user.events'")
    public void handle(DomainEvent domainEvent) {
        Map<String, Object> event = domainEvent.payload();
        String type = (String) event.get("eventType");
        if (!"USER_ACTIVATED".equals(type)) return;

        String email = (String) event.get("email");
        String name  = (String) event.get("firstName");
        if (email == null) return;

        log.info("Sending welcome email to {}", email);

        // Send welcome email when account becomes fully verified and active
        String subject = "Welcome to " + companyName + "!";
        String body = buildWelcomeHtml(name, email);
        emailService.sendRawEmail(email, subject, body);
    }

    private String buildWelcomeHtml(String name, String email) {
        return "<!DOCTYPE html><html><body style='font-family:Arial,sans-serif;background:#f1f5f9;'>"
                + "<div style='max-width:600px;margin:40px auto;background:#fff;"
                + "border-radius:12px;overflow:hidden;'>"
                + "<div style='background:#1e40af;padding:28px 32px;'>"
                + "<h1 style='color:#fff;margin:0;font-size:20px;'>" + companyName + "</h1></div>"
                + "<div style='padding:32px;'>"
                + "<h2 style='color:#1e293b;'>Welcome, " + name + "!</h2>"
                + "<p style='color:#475569;'>Your account has been verified and activated successfully.</p>"
                + "<p style='color:#475569;'>You can now:</p>"
                + "<ul style='color:#475569;'>"
                + "<li>Browse and purchase insurance plans</li>"
                + "<li>Manage your policies</li>"
                + "<li>Submit and track claims</li>"
                + "<li>Make premium payments online</li>"
                + "</ul>"
                + "<div style='margin:24px 0;'>"
                + "<a href='" + frontendUrl + "' style='background:#1e40af;color:#fff;"
                + "padding:12px 28px;border-radius:8px;text-decoration:none;"
                + "font-size:14px;font-weight:500;display:inline-block;'>Get Started</a></div>"
                + "</div>"
                + "<div style='background:#f8fafc;padding:20px 32px;border-top:1px solid #e2e8f0;'>"
                + "<p style='color:#94a3b8;font-size:12px;margin:0;'>"
                + "This email was sent to " + email + "</p></div>"
                + "</div></body></html>";
    }
}
