package com.insurance.payment.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.insurance.payment.entity.*;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Data @Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaymentResponse {
    private UUID id;
    private String transactionNumber;
    private UUID policyId;
    private String policyNumber;
    private UUID customerId;
    private TransactionType transactionType;
    private BigDecimal amount;
    private String currency;
    private PaymentStatus status;
    private String paymentMethod;
    private String gatewayName;
    private String gatewayOrderId;
    private String gatewayPaymentId;
    private String utrNumber;
    private String description;
    private String failureReason;
    private LocalDate premiumPeriodFrom;
    private LocalDate premiumPeriodTo;
    private UUID originalTransactionId;
    private String refundReason;
    private ZonedDateTime createdAt;
    private ZonedDateTime processedAt;

    public static PaymentResponse from(PaymentTransaction t) {
        return PaymentResponse.builder()
                .id(t.getId())
                .transactionNumber(t.getTransactionNumber())
                .policyId(t.getPolicyId())
                .policyNumber(t.getPolicyNumber())
                .customerId(t.getCustomerId())
                .transactionType(t.getTransactionType())
                .amount(t.getAmount())
                .currency(t.getCurrency())
                .status(t.getStatus())
                .paymentMethod(t.getPaymentMethod())
                .gatewayName(t.getGatewayName())
                .gatewayOrderId(t.getGatewayOrderId())
                .gatewayPaymentId(t.getGatewayPaymentId())
                .utrNumber(t.getUtrNumber())
                .description(t.getDescription())
                .failureReason(t.getFailureReason())
                .premiumPeriodFrom(t.getPremiumPeriodFrom())
                .premiumPeriodTo(t.getPremiumPeriodTo())
                .originalTransactionId(t.getOriginalTransactionId())
                .refundReason(t.getRefundReason())
                .createdAt(t.getCreatedAt())
                .processedAt(t.getProcessedAt())
                .build();
    }
}
