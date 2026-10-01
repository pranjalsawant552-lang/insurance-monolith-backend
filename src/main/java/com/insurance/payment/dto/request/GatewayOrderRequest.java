package com.insurance.payment.dto.request;

import lombok.Data;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Data
public class GatewayOrderRequest {
    private UUID policyId;
    private String policyNumber;
    private UUID customerId;
    private BigDecimal amount;
    private String currency;
    private String description;
    private UUID billingScheduleId;
}
