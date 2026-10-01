package com.insurance.payment.dto.request;

import com.insurance.payment.entity.TransactionType;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class CreatePaymentRequest {

    @NotNull  private UUID policyId;
    @NotBlank private String policyNumber;
    @NotNull  private UUID customerId;
    @NotNull  private TransactionType transactionType;

    @NotNull
    @DecimalMin(value = "1", message = "Amount must be greater than 0")
    private BigDecimal amount;

    @NotBlank private String paymentMethod;   // UPI, NET_BANKING, CARD, NEFT, RTGS, CASH
    private String gatewayName;              // RAZORPAY, STRIPE, MANUAL

    private String description;

    // For premium payments
    private LocalDate premiumPeriodFrom;
    private LocalDate premiumPeriodTo;
    private LocalDate premiumDueDate;
    private UUID billingScheduleId;

    // For claim settlement / refund
    private String bankAccountNumber;
    private String ifscCode;
}
