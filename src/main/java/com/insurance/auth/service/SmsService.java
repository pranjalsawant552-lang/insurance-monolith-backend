package com.insurance.auth.service;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SmsService {

    @Value("${twilio.account-sid}")
    private String accountSid;

    @Value("${twilio.auth-token}")
    private String authToken;

    @Value("${twilio.from-number}")
    private String fromNumber;

    @Value("${app.company-name}")
    private String companyName;

    @PostConstruct
    public void init() {
        if (accountSid != null && !accountSid.isBlank()) {
            Twilio.init(accountSid, authToken);
            log.info("Twilio SMS service initialized");
        } else {
            log.warn("Twilio credentials not configured — SMS will be logged only");
        }
    }

    @Async
    public void sendMobileVerificationOtp(String mobileNumber, String otp) {
        String text = companyName + ": Your mobile verification OTP is " + otp
                + ". Valid for 10 minutes. Do not share.";
        send(mobileNumber, text);
    }

    @Async
    public void sendLoginOtp(String mobileNumber, String otp) {
        String text = companyName + ": Your login OTP is " + otp
                + ". Valid for 10 minutes. Do not share.";
        send(mobileNumber, text);
    }

    @Async
    public void sendForgotPasswordOtp(String mobileNumber, String otp) {
        String text = companyName + ": Your password reset OTP is " + otp
                + ". Valid for 10 minutes. Do not share.";
        send(mobileNumber, text);
    }

    @Async
    public void sendUnlockOtp(String mobileNumber, String otp) {
        String text = companyName + ": Your account unlock OTP is " + otp
                + ". Valid for 10 minutes. Do not share.";
        send(mobileNumber, text);
    }

    @Async
    public void sendDeviceTrustOtp(String mobileNumber, String otp) {
        String text = companyName + ": New device login detected. OTP: " + otp
                + ". Valid for 10 minutes. If not you, change your password.";
        send(mobileNumber, text);
    }

    private void send(String toNumber, String text) {
        if (accountSid == null || accountSid.isBlank()) {
            log.info("[SMS-DEV] To: {} | Message: {}", toNumber, text);
            return;
        }
        try {
            Message message = Message.creator(
                    new PhoneNumber(toNumber),
                    new PhoneNumber(fromNumber),
                    text
            ).create();
            log.info("SMS sent to {} | SID: {}", toNumber, message.getSid());
        } catch (Exception e) {
            log.error("Failed to send SMS to {}: {}", toNumber, e.getMessage());
        }
    }
}
