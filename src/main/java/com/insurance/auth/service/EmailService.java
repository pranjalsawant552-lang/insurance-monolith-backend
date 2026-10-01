package com.insurance.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.company-name}")
    private String companyName;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    // ── Email Verification ────────────────────────────────────────────────────

    @Async
    public void sendEmailVerificationOtp(String toEmail, String firstName, String otp) {
        String subject = companyName + " - Verify your email address";
        String body = buildHtml(firstName,
                "Verify Your Email Address",
                "Thank you for registering with " + companyName + ". "
                        + "Please use the following OTP to verify your email address:",
                otp,
                "This OTP is valid for 10 minutes. Do not share it with anyone.");
        sendEmail(toEmail, subject, body);
    }

    // ── Mobile Verification ───────────────────────────────────────────────────

    @Async
    public void sendMobileVerificationEmail(String toEmail, String firstName, String otp) {
        String subject = companyName + " - Verify your mobile number";
        String body = buildHtml(firstName,
                "Verify Your Mobile Number",
                "Please use the OTP below to verify your mobile number:",
                otp,
                "This OTP is valid for 10 minutes.");
        sendEmail(toEmail, subject, body);
    }

    // ── Forgot Password ───────────────────────────────────────────────────────

    @Async
    public void sendForgotPasswordOtp(String toEmail, String firstName, String otp) {
        String subject = companyName + " - Password reset OTP";
        String body = buildHtml(firstName,
                "Reset Your Password",
                "We received a request to reset your password. Use the OTP below:",
                otp,
                "This OTP is valid for 10 minutes. If you didn't request a password reset, please ignore this email.");
        sendEmail(toEmail, subject, body);
    }

    // ── Account Locked ────────────────────────────────────────────────────────

    @Async
    public void sendAccountLockedEmail(String toEmail, String firstName, String otp) {
        String subject = companyName + " - Account locked";
        String body = buildHtml(firstName,
                "Your Account Has Been Locked",
                "Your account has been locked due to multiple failed login attempts. "
                        + "Use the OTP below to unlock your account:",
                otp,
                "If this wasn't you, please contact support immediately at " + fromEmail);
        sendEmail(toEmail, subject, body);
    }

    // ── Device Trust ──────────────────────────────────────────────────────────

    @Async
    public void sendDeviceTrustOtp(String toEmail, String firstName, String otp, String deviceName, String ip) {
        String subject = companyName + " - New device login detected";
        String body = buildHtml(firstName,
                "New Device Login",
                "A login was attempted from a new device: <strong>" + deviceName + "</strong> (IP: " + ip + "). "
                        + "Use the OTP below to trust this device:",
                otp,
                "If this wasn't you, your account may be at risk. Please change your password immediately.");
        sendEmail(toEmail, subject, body);
    }

    // ── Account Unlocked ──────────────────────────────────────────────────────

    @Async
    public void sendAccountUnlockedEmail(String toEmail, String firstName) {
        String subject = companyName + " - Account unlocked";
        String body = "<html><body style='font-family:Arial,sans-serif;'>"
                + "<div style='max-width:600px;margin:auto;padding:20px;'>"
                + "<h2 style='color:#2563eb;'>Account Unlocked</h2>"
                + "<p>Hi " + firstName + ",</p>"
                + "<p>Your account has been successfully unlocked. You can now log in.</p>"
                + "<p>If you did not request this, please contact support immediately.</p>"
                + "<p>Best regards,<br/>" + companyName + " Team</p>"
                + "</div></body></html>";
        sendEmail(toEmail, subject, body);
    }

    // ── Password Changed ──────────────────────────────────────────────────────

    @Async
    public void sendPasswordChangedEmail(String toEmail, String firstName) {
        String subject = companyName + " - Password changed successfully";
        String body = "<html><body style='font-family:Arial,sans-serif;'>"
                + "<div style='max-width:600px;margin:auto;padding:20px;'>"
                + "<h2 style='color:#2563eb;'>Password Changed</h2>"
                + "<p>Hi " + firstName + ",</p>"
                + "<p>Your password was changed successfully. All existing sessions have been logged out.</p>"
                + "<p>If you did not make this change, please contact support immediately.</p>"
                + "<p>Best regards,<br/>" + companyName + " Team</p>"
                + "</div></body></html>";
        sendEmail(toEmail, subject, body);
    }

    // ── Internal sender ───────────────────────────────────────────────────────

    private void sendEmail(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail, companyName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("Email sent to {}: {}", to, subject);
        } catch (MessagingException | java.io.UnsupportedEncodingException e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }

    private String buildHtml(String firstName, String heading, String body, String otp, String footer) {
        return "<html><body style='font-family:Arial,sans-serif;background:#f3f4f6;'>"
                + "<div style='max-width:600px;margin:40px auto;background:#fff;border-radius:12px;"
                + "box-shadow:0 2px 8px rgba(0,0,0,0.08);overflow:hidden;'>"
                + "<div style='background:#2563eb;padding:24px 32px;'>"
                + "<h1 style='color:#fff;margin:0;font-size:22px;'>" + companyName + "</h1>"
                + "</div>"
                + "<div style='padding:32px;'>"
                + "<h2 style='color:#1e293b;margin-top:0;'>" + heading + "</h2>"
                + "<p style='color:#475569;'>Hi " + firstName + ",</p>"
                + "<p style='color:#475569;'>" + body + "</p>"
                + "<div style='margin:32px 0;text-align:center;'>"
                + "<div style='display:inline-block;background:#f1f5f9;border-radius:8px;"
                + "padding:16px 40px;font-size:36px;font-weight:bold;letter-spacing:12px;color:#2563eb;'>"
                + otp + "</div>"
                + "</div>"
                + "<p style='color:#94a3b8;font-size:13px;'>" + footer + "</p>"
                + "</div>"
                + "<div style='background:#f8fafc;padding:16px 32px;text-align:center;"
                + "color:#94a3b8;font-size:12px;'>"
                + "© " + java.time.Year.now().getValue() + " " + companyName
                + ". Do not reply to this email."
                + "</div>"
                + "</div></body></html>";
    }
}
