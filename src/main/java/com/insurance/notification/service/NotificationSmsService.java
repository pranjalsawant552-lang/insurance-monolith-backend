package com.insurance.notification.service;

import com.insurance.notification.entity.NotificationLog;
import com.insurance.notification.repository.NotificationLogRepository;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationSmsService {

    private final NotificationLogRepository notificationLogRepository;

    @Value("${twilio.account-sid}") private String accountSid;
    @Value("${twilio.auth-token}")  private String authToken;
    @Value("${twilio.from-number}") private String fromNumber;
    @Value("${app.company-name}")   private String companyName;

    @PostConstruct
    public void init() {
        if (accountSid != null && !accountSid.isBlank()) {
            Twilio.init(accountSid, authToken);
            log.info("Twilio SMS initialised");
        } else {
            log.warn("Twilio not configured — SMS will be logged only");
        }
    }

    @Async
    public void sendPolicyIssued(String mobile, String policyNumber) {
        send(mobile, companyName + ": Policy " + policyNumber
                + " is now active. Keep this number safe for all future references.", "POLICY_ISSUED");
    }

    @Async
    public void sendPremiumDue(String mobile, String policyNumber,
                                String amount, String dueDate) {
        send(mobile, companyName + ": Premium of Rs." + amount
                + " due on " + dueDate + " for policy " + policyNumber
                + ". Pay now to keep your policy active.", "PREMIUM_DUE");
    }

    @Async
    public void sendPremiumOverdue(String mobile, String policyNumber, String amount) {
        send(mobile, companyName + " URGENT: Premium overdue for policy "
                + policyNumber + ". Pay Rs." + amount
                + " immediately to avoid lapse.", "PREMIUM_OVERDUE");
    }

    @Async
    public void sendPolicyLapsed(String mobile, String policyNumber) {
        send(mobile, companyName + ": Policy " + policyNumber
                + " has lapsed. Contact us immediately for reinstatement options.", "POLICY_LAPSED");
    }

    @Async
    public void sendClaimSubmitted(String mobile, String claimNumber) {
        send(mobile, companyName + ": Claim " + claimNumber
                + " received and under review. Track at our portal.", "CLAIM_SUBMITTED");
    }

    @Async
    public void sendClaimApproved(String mobile, String claimNumber, String amount) {
        send(mobile, companyName + ": Claim " + claimNumber
                + " approved for Rs." + amount
                + ". Settlement will be credited within 3-5 working days.", "CLAIM_APPROVED");
    }

    @Async
    public void sendClaimRejected(String mobile, String claimNumber) {
        send(mobile, companyName + ": Claim " + claimNumber
                + " could not be approved. Check email for details or contact support.", "CLAIM_REJECTED");
    }

    @Async
    public void sendClaimSettled(String mobile, String claimNumber, String amount) {
        send(mobile, companyName + ": Claim " + claimNumber
                + " settled. Rs." + amount
                + " transferred to your bank account.", "CLAIM_SETTLED");
    }

    @Async
    public void sendPaymentSuccess(String mobile, String txnNumber, String amount) {
        send(mobile, companyName + ": Payment of Rs." + amount
                + " received. Txn ref: " + txnNumber + ". Thank you.", "PAYMENT_SUCCESS");
    }

    @Async
    public void sendPaymentFailed(String mobile, String policyNumber, String amount) {
        send(mobile, companyName + ": Payment of Rs." + amount
                + " failed for policy " + policyNumber
                + ". Please retry to keep your policy active.", "PAYMENT_FAILED");
    }

    @Async
    public void sendKycVerified(String mobile) {
        send(mobile, companyName + ": Your KYC has been verified successfully. "
                + "You can now purchase insurance policies.", "KYC_VERIFIED");
    }

    @Async
    public void sendKycRejected(String mobile) {
        send(mobile, companyName + ": KYC verification failed. "
                + "Please re-submit correct documents via our portal.", "KYC_REJECTED");
    }

    private void send(String to, String text, String template) {
        NotificationLog logRow = NotificationLog.builder()
                .channel(NotificationLog.Channel.SMS)
                .template(template)
                .recipientAddress(to)
                .status(NotificationLog.DeliveryStatus.PENDING)
                .attemptCount(1)
                .build();
        logRow = notificationLogRepository.save(logRow);

        if (accountSid == null || accountSid.isBlank()) {
            log.info("[SMS-DEV] To: {} | {}", to, text);
            logRow.setStatus(NotificationLog.DeliveryStatus.SENT);
            logRow.setSentAt(ZonedDateTime.now());
            logRow.setFailureReason("DEV MODE — Twilio not configured, logged only, not actually sent");
            notificationLogRepository.save(logRow);
            return;
        }
        try {
            Message msg = Message.creator(new PhoneNumber(to), new PhoneNumber(fromNumber), text)
                    .create();
            log.info("SMS sent to {}", to);
            logRow.setStatus(NotificationLog.DeliveryStatus.SENT);
            logRow.setSentAt(ZonedDateTime.now());
            logRow.setProviderMessageId(msg.getSid());
            notificationLogRepository.save(logRow);
        } catch (Exception e) {
            log.error("SMS failed to {}: {}", to, e.getMessage());
            logRow.setStatus(NotificationLog.DeliveryStatus.FAILED);
            logRow.setFailureReason(e.getMessage());
            logRow.setNextRetryAt(ZonedDateTime.now().plusMinutes(5));
            notificationLogRepository.save(logRow);
        }
    }
}
