package com.insurance.notification.service;

import com.insurance.notification.entity.NotificationLog;
import com.insurance.notification.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.time.ZonedDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationEmailService {

    private final JavaMailSender mailSender;
    private final NotificationLogRepository notificationLogRepository;

    @Value("${spring.mail.username}")   private String fromEmail;
    @Value("${app.company-name}")       private String companyName;
    @Value("${app.frontend-url}")       private String frontendUrl;

    // ── Policy events ─────────────────────────────────────────────────────────

    @Async
    public void sendPolicyIssued(String toEmail, String customerName,
                                  String policyNumber, String insuranceType,
                                  String sumAssured, String premium, String commencementDate) {
        String subject = companyName + " - Policy Issued: " + policyNumber;
        String body = layout(customerName, "Your Policy is Now Active",
                "<p>Congratulations! Your <strong>" + insuranceType + "</strong> insurance policy has been issued successfully.</p>"
                + detailsTable(new String[][]{
                        {"Policy Number", policyNumber},
                        {"Sum Assured",   "₹" + sumAssured},
                        {"Premium",       "₹" + premium},
                        {"Start Date",    commencementDate}
                  })
                + cta("View Policy", frontendUrl + "/policies/" + policyNumber),
                "Keep this email safe. Your policy number is your reference for all future communications.");
        send(toEmail, subject, body, "POLICY_ISSUED");
    }

    @Async
    public void sendPolicyRenewalDue(String toEmail, String customerName,
                                      String policyNumber, String dueDate,
                                      String premiumAmount) {
        String subject = companyName + " - Premium Due: " + policyNumber;
        String body = layout(customerName, "Premium Payment Due",
                "<p>Your premium for policy <strong>" + policyNumber + "</strong> is due on <strong>" + dueDate + "</strong>.</p>"
                + detailsTable(new String[][]{
                        {"Policy Number", policyNumber},
                        {"Amount Due",    "₹" + premiumAmount},
                        {"Due Date",      dueDate}
                  })
                + cta("Pay Now", frontendUrl + "/payments?policy=" + policyNumber),
                "Please pay before the due date to avoid lapsing your policy.");
        send(toEmail, subject, body, "POLICY_RENEWAL_DUE");
    }

    @Async
    public void sendPolicyLapsed(String toEmail, String customerName, String policyNumber) {
        String subject = companyName + " - Policy Lapsed: " + policyNumber;
        String body = layout(customerName, "Your Policy Has Lapsed",
                "<p>Your policy <strong>" + policyNumber + "</strong> has lapsed due to non-payment of premium.</p>"
                + "<p>Please contact us immediately to explore reinstatement options.</p>"
                + cta("Contact Support", frontendUrl + "/support"),
                "Act quickly — reinstatement may be possible within a limited period.");
        send(toEmail, subject, body, "POLICY_LAPSED");
    }

    @Async
    public void sendPolicyCancelled(String toEmail, String customerName,
                                     String policyNumber, String reason) {
        String subject = companyName + " - Policy Cancelled: " + policyNumber;
        String body = layout(customerName, "Your Policy Has Been Cancelled",
                "<p>Your policy <strong>" + policyNumber + "</strong> has been cancelled.</p>"
                + detailsTable(new String[][]{{"Reason", reason}})
                + "<p>If you believe this is an error, please contact our support team immediately.</p>"
                + cta("Contact Support", frontendUrl + "/support"),
                "We are sorry to see you go.");
        send(toEmail, subject, body, "POLICY_CANCELLED");
    }

    // ── Claim events ──────────────────────────────────────────────────────────

    @Async
    public void sendClaimSubmitted(String toEmail, String customerName,
                                    String claimNumber, String policyNumber,
                                    String claimType, String claimedAmount) {
        String subject = companyName + " - Claim Received: " + claimNumber;
        String body = layout(customerName, "Your Claim Has Been Received",
                "<p>We have received your claim and it is now under review.</p>"
                + detailsTable(new String[][]{
                        {"Claim Number",  claimNumber},
                        {"Policy Number", policyNumber},
                        {"Claim Type",    claimType},
                        {"Claimed Amount","₹" + claimedAmount}
                  })
                + cta("Track Claim", frontendUrl + "/claims/" + claimNumber),
                "Our team will review your claim and get back to you within 3–5 working days.");
        send(toEmail, subject, body, "CLAIM_SUBMITTED");
    }

    @Async
    public void sendClaimDocumentsRequired(String toEmail, String customerName,
                                            String claimNumber, String documentList) {
        String subject = companyName + " - Documents Required: " + claimNumber;
        String body = layout(customerName, "Additional Documents Required",
                "<p>To process your claim <strong>" + claimNumber + "</strong>, we need the following documents:</p>"
                + "<div style='background:#f8fafc;border-radius:8px;padding:16px;margin:16px 0;'>"
                + "<p style='white-space:pre-line;color:#334155;'>" + documentList + "</p></div>"
                + cta("Upload Documents", frontendUrl + "/claims/" + claimNumber + "/documents"),
                "Please upload documents at the earliest to avoid delays in claim processing.");
        send(toEmail, subject, body, "CLAIM_DOCUMENTS_REQUIRED");
    }

    @Async
    public void sendClaimApproved(String toEmail, String customerName,
                                   String claimNumber, String approvedAmount) {
        String subject = companyName + " - Claim Approved: " + claimNumber;
        String body = layout(customerName, "Your Claim Has Been Approved",
                "<p>Great news! Your claim <strong>" + claimNumber + "</strong> has been approved.</p>"
                + detailsTable(new String[][]{
                        {"Claim Number",   claimNumber},
                        {"Approved Amount","₹" + approvedAmount}
                  })
                + "<p>The settlement amount will be disbursed to your registered bank account within 3–5 working days.</p>",
                "Thank you for choosing " + companyName + ".");
        send(toEmail, subject, body, "CLAIM_APPROVED");
    }

    @Async
    public void sendClaimRejected(String toEmail, String customerName,
                                   String claimNumber, String reason) {
        String subject = companyName + " - Claim Decision: " + claimNumber;
        String body = layout(customerName, "Your Claim Has Been Rejected",
                "<p>We regret to inform you that your claim <strong>" + claimNumber + "</strong> could not be approved.</p>"
                + detailsTable(new String[][]{{"Reason", reason}})
                + "<p>If you wish to appeal this decision, please contact our claims department within 30 days.</p>"
                + cta("Contact Claims Team", frontendUrl + "/support"),
                "We understand this may be disappointing and apologise for any inconvenience.");
        send(toEmail, subject, body, "CLAIM_REJECTED");
    }

    @Async
    public void sendClaimSettled(String toEmail, String customerName,
                                  String claimNumber, String settledAmount) {
        String subject = companyName + " - Claim Settled: " + claimNumber;
        String body = layout(customerName, "Claim Settlement Completed",
                "<p>Your claim <strong>" + claimNumber + "</strong> has been settled successfully.</p>"
                + detailsTable(new String[][]{
                        {"Claim Number",   claimNumber},
                        {"Settled Amount", "₹" + settledAmount}
                  })
                + "<p>The amount has been transferred to your registered bank account.</p>",
                "Please allow 1–2 business days for the credit to reflect in your account.");
        send(toEmail, subject, body, "CLAIM_SETTLED");
    }

    // ── Payment events ────────────────────────────────────────────────────────

    @Async
    public void sendPaymentSuccess(String toEmail, String customerName,
                                    String txnNumber, String policyNumber,
                                    String amount, String paymentMethod) {
        String subject = companyName + " - Payment Received: ₹" + amount;
        String body = layout(customerName, "Payment Successful",
                "<p>We have received your premium payment. Thank you!</p>"
                + detailsTable(new String[][]{
                        {"Transaction No.", txnNumber},
                        {"Policy Number",   policyNumber},
                        {"Amount Paid",     "₹" + amount},
                        {"Payment Method",  paymentMethod}
                  }),
                "Please keep this as your payment confirmation.");
        send(toEmail, subject, body, "PAYMENT_SUCCESS");
    }

    @Async
    public void sendPaymentFailed(String toEmail, String customerName,
                                   String policyNumber, String amount, String reason) {
        String subject = companyName + " - Payment Failed for Policy " + policyNumber;
        String body = layout(customerName, "Payment Could Not Be Processed",
                "<p>Your premium payment of <strong>₹" + amount + "</strong> for policy <strong>"
                + policyNumber + "</strong> could not be processed.</p>"
                + detailsTable(new String[][]{{"Reason", reason}})
                + cta("Retry Payment", frontendUrl + "/payments?policy=" + policyNumber),
                "Please retry the payment to keep your policy active.");
        send(toEmail, subject, body, "PAYMENT_FAILED");
    }

    @Async
    public void sendPremiumOverdue(String toEmail, String customerName,
                                    String policyNumber, String amount, String dueDate) {
        String subject = companyName + " - URGENT: Overdue Premium for " + policyNumber;
        String body = layout(customerName, "Premium Overdue — Action Required",
                "<p style='color:#dc2626;font-weight:500;'>Your premium payment is overdue. "
                + "Please pay immediately to prevent your policy from lapsing.</p>"
                + detailsTable(new String[][]{
                        {"Policy Number", policyNumber},
                        {"Overdue Amount","₹" + amount},
                        {"Was Due On",    dueDate}
                  })
                + cta("Pay Now", frontendUrl + "/payments?policy=" + policyNumber),
                "Your policy will lapse if payment is not received within the grace period.");
        send(toEmail, subject, body, "PREMIUM_OVERDUE");
    }

    // ── KYC events ────────────────────────────────────────────────────────────

    @Async
    public void sendKycVerified(String toEmail, String customerName) {
        String subject = companyName + " - KYC Verified Successfully";
        String body = layout(customerName, "Your KYC is Verified",
                "<p>Your KYC documents have been verified successfully. You are now eligible to purchase insurance policies.</p>"
                + cta("Browse Plans", frontendUrl + "/plans"),
                "Welcome to " + companyName + "!");
        send(toEmail, subject, body, "KYC_VERIFIED");
    }

    @Async
    public void sendKycRejected(String toEmail, String customerName, String reason) {
        String subject = companyName + " - KYC Verification Failed";
        String body = layout(customerName, "KYC Verification Could Not Be Completed",
                "<p>Unfortunately, your KYC verification could not be completed.</p>"
                + detailsTable(new String[][]{{"Reason", reason}})
                + cta("Re-submit Documents", frontendUrl + "/kyc"),
                "Please re-submit the correct documents to complete verification.");
        send(toEmail, subject, body, "KYC_REJECTED");
    }

    // ── Private: shared HTML builder and sender ───────────────────────────────

    private String layout(String name, String heading, String content, String footer) {
        return "<!DOCTYPE html><html><body style='margin:0;padding:0;font-family:Arial,sans-serif;"
                + "background:#f1f5f9;'>"
                + "<div style='max-width:600px;margin:40px auto;background:#fff;"
                + "border-radius:12px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,0.08);'>"
                + "<div style='background:#1e40af;padding:28px 32px;'>"
                + "<h1 style='color:#fff;margin:0;font-size:20px;letter-spacing:-0.3px;'>"
                + companyName + "</h1></div>"
                + "<div style='padding:32px;'>"
                + "<h2 style='color:#1e293b;font-size:20px;margin:0 0 16px;'>" + heading + "</h2>"
                + "<p style='color:#475569;margin:0 0 16px;'>Dear " + name + ",</p>"
                + content
                + "</div>"
                + "<div style='background:#f8fafc;padding:20px 32px;border-top:1px solid #e2e8f0;'>"
                + "<p style='color:#94a3b8;font-size:12px;margin:0;'>" + footer + "</p>"
                + "<p style='color:#94a3b8;font-size:12px;margin:8px 0 0;'>© "
                + java.time.Year.now().getValue() + " " + companyName
                + " &nbsp;|&nbsp; <a href='mailto:" + fromEmail
                + "' style='color:#94a3b8;'>Contact Support</a></p>"
                + "</div></div></body></html>";
    }

    private String detailsTable(String[][] rows) {
        StringBuilder sb = new StringBuilder(
                "<table style='width:100%;border-collapse:collapse;margin:20px 0;'>");
        for (String[] row : rows) {
            sb.append("<tr>")
              .append("<td style='padding:10px 12px;background:#f8fafc;color:#64748b;"
                      + "font-size:13px;border-bottom:1px solid #e2e8f0;width:40%;'>")
              .append(row[0]).append("</td>")
              .append("<td style='padding:10px 12px;color:#1e293b;font-size:13px;"
                      + "font-weight:500;border-bottom:1px solid #e2e8f0;'>")
              .append(row[1]).append("</td></tr>");
        }
        sb.append("</table>");
        return sb.toString();
    }

    private String cta(String label, String url) {
        return "<div style='margin:24px 0;'>"
                + "<a href='" + url + "' style='background:#1e40af;color:#fff;padding:12px 28px;"
                + "border-radius:8px;text-decoration:none;font-size:14px;font-weight:500;"
                + "display:inline-block;'>" + label + "</a></div>";
    }

    // Called by consumers that build their own full HTML body
    public void sendRawEmail(String to, String subject, String htmlBody) {
        send(to, subject, htmlBody, "RAW");
    }

    private void send(String to, String subject, String htmlBody) {
        send(to, subject, htmlBody, "UNSPECIFIED");
    }

    /**
     * Persists a NotificationLog row before attempting the send and updates it
     * with the outcome — replaces the "sends are fire-and-forget, nothing is
     * stored" gap flagged in the original audit (§4.12). recipientId is left
     * null here deliberately: none of the 14 public methods on this class
     * currently receive a customer/user id (only an address + display name),
     * and threading one through would mean changing every method signature on
     * this class plus every consumer call site — a large, hard-to-verify
     * change deferred rather than risked in this pass (see Phase 7 changelog).
     * recipientAddress (the actual "to" value) is captured either way, which
     * covers the audit and delivery-status use cases this table exists for.
     */
    private void send(String to, String subject, String htmlBody, String template) {
        NotificationLog logRow = NotificationLog.builder()
                .channel(NotificationLog.Channel.EMAIL)
                .template(template)
                .recipientAddress(to)
                .subject(subject)
                .status(NotificationLog.DeliveryStatus.PENDING)
                .attemptCount(1)
                .build();
        logRow = notificationLogRepository.save(logRow);

        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setFrom(fromEmail, companyName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(msg);
            log.info("Email sent → {} | {}", to, subject);

            logRow.setStatus(NotificationLog.DeliveryStatus.SENT);
            logRow.setSentAt(ZonedDateTime.now());
            notificationLogRepository.save(logRow);
        } catch (MessagingException | UnsupportedEncodingException e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
            logRow.setStatus(NotificationLog.DeliveryStatus.FAILED);
            logRow.setFailureReason(e.getMessage());
            logRow.setNextRetryAt(ZonedDateTime.now().plusMinutes(5));
            notificationLogRepository.save(logRow);
        }
    }
}
