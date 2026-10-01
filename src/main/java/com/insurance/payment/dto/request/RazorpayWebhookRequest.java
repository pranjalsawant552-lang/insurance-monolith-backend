package com.insurance.payment.dto.request;

import lombok.Data;
import java.util.Map;

@Data
public class RazorpayWebhookRequest {
    private String event;               // payment.captured, payment.failed, refund.created
    private Map<String, Object> payload;
}
