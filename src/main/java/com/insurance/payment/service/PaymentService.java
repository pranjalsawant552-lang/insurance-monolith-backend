package com.insurance.payment.service;

import com.insurance.payment.dto.request.*;
import com.insurance.payment.dto.response.*;
import com.insurance.payment.entity.*;
import com.insurance.payment.event.PaymentEventPublisher;
import com.insurance.payment.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final BillingScheduleRepository billingRepository;
    private final PaymentEventPublisher eventPublisher;
    private final com.insurance.payment.event.AuditEventPublisher auditEventPublisher;

    @Value("${payment.gateway.razorpay.key-id:}")
    private String razorpayKeyId;

    @Value("${payment.gateway.razorpay.key-secret:}")
    private String razorpayKeySecret;

    @Value("${payment.gateway.razorpay.webhook-secret:}")
    private String razorpayWebhookSecret;

    // ── Create Razorpay order (step 1 of online payment) ─────────────────────

    @Transactional
    public Map<String, Object> createGatewayOrder(GatewayOrderRequest req, UUID companyId, UUID createdBy) {
        String txnNumber = generateTransactionNumber("PRE");

        // Convert to paise (smallest unit)
        long amountPaise = req.getAmount().multiply(BigDecimal.valueOf(100)).longValue();

        // Store pending transaction
        PaymentTransaction txn = PaymentTransaction.builder()
                .transactionNumber(txnNumber)
                .policyId(req.getPolicyId())
                .policyNumber(req.getPolicyNumber())
                .customerId(req.getCustomerId())
                .companyId(companyId)
                .transactionType(TransactionType.PREMIUM)
                .amount(req.getAmount())
                .currency("INR")
                .status(PaymentStatus.PENDING)
                .gatewayName("RAZORPAY")
                .description(req.getDescription())
                .createdBy(createdBy)
                .build();

        txn = paymentRepository.save(txn);

        // Build Razorpay order params (actual API call would go here)
        Map<String, Object> orderParams = new HashMap<>();
        orderParams.put("amount", amountPaise);
        orderParams.put("currency", "INR");
        orderParams.put("receipt", txnNumber);
        orderParams.put("notes", Map.of(
                "policyNumber", req.getPolicyNumber(),
                "transactionId", txn.getId().toString()
        ));

        // If Razorpay credentials are configured, call the API
        String gatewayOrderId = simulateOrCallRazorpay(orderParams);
        txn.setGatewayOrderId(gatewayOrderId);
        paymentRepository.save(txn);

        return Map.of(
                "transactionId", txn.getId().toString(),
                "transactionNumber", txnNumber,
                "gatewayOrderId", gatewayOrderId,
                "amount", amountPaise,
                "currency", "INR",
                "keyId", razorpayKeyId
        );
    }

    // ── Verify payment after frontend completes it (step 2) ───────────────────

    @Transactional
    public PaymentResponse verifyAndCapturePayment(String transactionId,
                                                    String gatewayOrderId,
                                                    String gatewayPaymentId,
                                                    String gatewaySignature) {
        PaymentTransaction txn = paymentRepository.findById(UUID.fromString(transactionId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));

        if (txn.getStatus() != PaymentStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Transaction already processed: " + txn.getStatus());
        }

        // AUDIT FINDING (§4.10): this used to be
        // `if (signatureValid || razorpayKeySecret.isBlank())` — meaning
        // when Razorpay wasn't configured, EVERY payment verification
        // succeeded regardless of whether any real signature was ever
        // provided. That's not just a fake-success cosmetic issue: it's a
        // forgeable payment-confirmation endpoint — any caller could POST
        // an arbitrary transaction ID with a garbage signature and have it
        // marked SUCCESS and billing marked paid. Signature verification
        // is no longer bypassable; if the gateway isn't configured, this
        // whole flow already fails earlier at order-creation time (see
        // simulateOrCallRazorpay), so reaching here at all means it WAS
        // configured and a real signature is expected.
        boolean signatureValid = verifyRazorpaySignature(gatewayOrderId, gatewayPaymentId, gatewaySignature);

        if (signatureValid) {
            txn.setStatus(PaymentStatus.SUCCESS);
            txn.setGatewayPaymentId(gatewayPaymentId);
            txn.setGatewaySignature(gatewaySignature);
            txn.setProcessedAt(ZonedDateTime.now());

            // Mark billing schedule as paid
            markBillingPaid(txn);

            txn = paymentRepository.save(txn);
            eventPublisher.publishPaymentSuccess(txn);
            auditEventPublisher.publish("PAYMENT", txn.getId().toString(), "PAYMENT_CAPTURED",
                    java.util.Map.of("status", "PENDING"),
                    java.util.Map.of("status", "SUCCESS", "amount", txn.getAmount().toString(), "gatewayPaymentId", gatewayPaymentId != null ? gatewayPaymentId : ""),
                    "Payment " + txn.getTransactionNumber() + " captured for " + txn.getAmount());
            log.info("Payment verified and captured: {}", txn.getTransactionNumber());
        } else {
            txn.setStatus(PaymentStatus.FAILED);
            txn.setFailureReason("Signature verification failed");
            txn = paymentRepository.save(txn);
            eventPublisher.publishPaymentFailed(txn);
            auditEventPublisher.publish("PAYMENT", txn.getId().toString(), "PAYMENT_FAILED",
                    java.util.Map.of("status", "PENDING"),
                    java.util.Map.of("status", "FAILED", "failureReason", "Signature verification failed"),
                    "Payment " + txn.getTransactionNumber() + " failed signature verification");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment signature verification failed.");
        }

        return PaymentResponse.from(txn);
    }

    // ── Razorpay Webhook handler ───────────────────────────────────────────────

    @Transactional
    public void handleRazorpayWebhook(RazorpayWebhookRequest webhook,
                                      String webhookSignature, String rawBody) {
        // Verify webhook authenticity
        if (!verifyWebhookSignature(rawBody, webhookSignature)) {
            log.warn("Razorpay webhook signature verification failed");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid webhook signature");
        }

        String event = webhook.getEvent();
        log.info("Razorpay webhook received: {}", event);

        switch (event) {
            case "payment.captured" -> handlePaymentCaptured(webhook.getPayload());
            case "payment.failed"   -> handlePaymentFailed(webhook.getPayload());
            case "refund.created"   -> handleRefundCreated(webhook.getPayload());
            default -> log.debug("Unhandled Razorpay webhook event: {}", event);
        }
    }

    // ── Manual / offline payment recording ───────────────────────────────────

    @Transactional
    public PaymentResponse recordManualPayment(CreatePaymentRequest req,
                                               UUID companyId, UUID recordedBy) {
        String txnNumber = generateTransactionNumber(req.getTransactionType().name().substring(0, 3));

        PaymentTransaction txn = PaymentTransaction.builder()
                .transactionNumber(txnNumber)
                .policyId(req.getPolicyId())
                .policyNumber(req.getPolicyNumber())
                .customerId(req.getCustomerId())
                .companyId(companyId)
                .transactionType(req.getTransactionType())
                .amount(req.getAmount())
                .currency("INR")
                .status(PaymentStatus.SUCCESS)
                .paymentMethod(req.getPaymentMethod())
                .gatewayName("MANUAL")
                .description(req.getDescription())
                .bankAccountNumber(req.getBankAccountNumber())
                .ifscCode(req.getIfscCode())
                .premiumPeriodFrom(req.getPremiumPeriodFrom())
                .premiumPeriodTo(req.getPremiumPeriodTo())
                .premiumDueDate(req.getPremiumDueDate())
                .processedAt(ZonedDateTime.now())
                .createdBy(recordedBy)
                .build();

        txn = paymentRepository.save(txn);
        markBillingPaid(txn);
        eventPublisher.publishPaymentSuccess(txn);
        auditEventPublisher.publish("PAYMENT", txn.getId().toString(), "MANUAL_PAYMENT_RECORDED",
                null,
                java.util.Map.of("amount", txn.getAmount().toString(), "paymentMethod", req.getPaymentMethod(),
                        "transactionType", txn.getTransactionType().name(), "policyNumber", req.getPolicyNumber()),
                "Manual payment " + txnNumber + " recorded for policy " + req.getPolicyNumber() + ": " + txn.getAmount());

        log.info("Manual payment recorded: {} for policy {}", txnNumber, req.getPolicyNumber());
        return PaymentResponse.from(txn);
    }

    // ── Claim settlement disbursement ─────────────────────────────────────────

    @Transactional
    public PaymentResponse disburseClaimSettlement(CreatePaymentRequest req,
                                                    UUID companyId, UUID processedBy) {
        if (req.getBankAccountNumber() == null || req.getIfscCode() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Bank account details are required for claim settlement disbursement.");
        }

        String txnNumber = generateTransactionNumber("CLM");

        PaymentTransaction txn = PaymentTransaction.builder()
                .transactionNumber(txnNumber)
                .policyId(req.getPolicyId())
                .policyNumber(req.getPolicyNumber())
                .customerId(req.getCustomerId())
                .companyId(companyId)
                .transactionType(TransactionType.CLAIM_SETTLEMENT)
                .amount(req.getAmount())
                .currency("INR")
                .status(PaymentStatus.PROCESSING)
                .paymentMethod("NEFT")
                .gatewayName("MANUAL")
                .bankAccountNumber(req.getBankAccountNumber())
                .ifscCode(req.getIfscCode())
                .description("Claim settlement: " + req.getPolicyNumber())
                .createdBy(processedBy)
                .build();

        txn = paymentRepository.save(txn);
        auditEventPublisher.publish("PAYMENT", txn.getId().toString(), "CLAIM_SETTLEMENT_INITIATED",
                null,
                java.util.Map.of("amount", txn.getAmount().toString(), "status", "PROCESSING", "policyNumber", req.getPolicyNumber()),
                "Claim settlement disbursement " + txnNumber + " initiated for " + req.getAmount()
                        + " on policy " + req.getPolicyNumber() + " — awaiting UTR confirmation");
        log.info("Claim settlement initiated: {} amount={}", txnNumber, req.getAmount());
        return PaymentResponse.from(txn);
    }

    // ── Confirm disbursement with UTR ──────────────────────────────────────────

    @Transactional
    public PaymentResponse confirmDisbursement(UUID txnId, String utrNumber, UUID confirmedBy) {
        PaymentTransaction txn = findOrThrow(txnId);
        if (txn.getStatus() != PaymentStatus.PROCESSING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only PROCESSING transactions can be confirmed.");
        }
        txn.setUtrNumber(utrNumber);
        txn.setStatus(PaymentStatus.SUCCESS);
        txn.setProcessedAt(ZonedDateTime.now());
        txn = paymentRepository.save(txn);
        eventPublisher.publishPaymentSuccess(txn);
        auditEventPublisher.publish("PAYMENT", txn.getId().toString(), "DISBURSEMENT_CONFIRMED",
                java.util.Map.of("status", "PROCESSING"),
                java.util.Map.of("status", "SUCCESS", "utrNumber", utrNumber != null ? utrNumber : ""),
                "Disbursement " + txn.getTransactionNumber() + " confirmed with UTR " + utrNumber
                        + " by " + confirmedBy);
        return PaymentResponse.from(txn);
    }

    // ── Refund (full or partial) ────────────────────────────────────────────

    @Transactional
    public PaymentResponse refund(UUID originalTxnId, BigDecimal refundAmount, String reason, UUID processedBy) {
        PaymentTransaction original = findOrThrow(originalTxnId);

        if (original.getStatus() != PaymentStatus.SUCCESS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only a SUCCESS transaction can be refunded. Current status: " + original.getStatus());
        }
        if (refundAmount == null || refundAmount.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Refund amount must be positive.");
        }

        BigDecimal alreadyRefunded = paymentRepository.sumRefundedAmount(originalTxnId);
        BigDecimal remaining = original.getAmount().subtract(alreadyRefunded);
        if (refundAmount.compareTo(remaining) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Refund amount " + refundAmount + " exceeds remaining refundable balance " + remaining
                            + " (already refunded: " + alreadyRefunded + " of " + original.getAmount() + ").");
        }

        String txnNumber = generateTransactionNumber("REF");
        PaymentTransaction refundTxn = PaymentTransaction.builder()
                .transactionNumber(txnNumber)
                .policyId(original.getPolicyId())
                .policyNumber(original.getPolicyNumber())
                .customerId(original.getCustomerId())
                .companyId(original.getCompanyId())
                .transactionType(TransactionType.REFUND)
                .amount(refundAmount)
                .currency(original.getCurrency())
                .status(PaymentStatus.SUCCESS)
                .paymentMethod(original.getPaymentMethod())
                .gatewayName(original.getGatewayName())
                .originalTransactionId(originalTxnId)
                .refundReason(reason)
                .description("Refund of " + original.getTransactionNumber() + (reason != null ? ": " + reason : ""))
                .processedAt(ZonedDateTime.now())
                .createdBy(processedBy)
                .build();

        refundTxn = paymentRepository.save(refundTxn);

        boolean fullyRefunded = alreadyRefunded.add(refundAmount).compareTo(original.getAmount()) >= 0;
        if (fullyRefunded && original.getStatus() == PaymentStatus.SUCCESS) {
            original.setStatus(PaymentStatus.REFUNDED);
            paymentRepository.save(original);
        }

        eventPublisher.publishPaymentSuccess(refundTxn);
        auditEventPublisher.publish("PAYMENT", refundTxn.getId().toString(), "REFUND",
                null,
                java.util.Map.of("originalTransactionId", originalTxnId.toString(), "amount", refundAmount.toString(),
                        "reason", reason != null ? reason : "", "fullyRefunded", String.valueOf(fullyRefunded)),
                "Refund " + txnNumber + " of " + refundAmount + " issued against " + original.getTransactionNumber()
                        + (fullyRefunded ? " (fully refunded)" : " (partial — " + remaining.subtract(refundAmount) + " remaining)"));

        log.info("Refund issued: {} amount={} against original {}", txnNumber, refundAmount, original.getTransactionNumber());
        return PaymentResponse.from(refundTxn);
    }

    public BigDecimal getRefundableBalance(UUID originalTxnId) {
        PaymentTransaction original = findOrThrow(originalTxnId);
        BigDecimal alreadyRefunded = paymentRepository.sumRefundedAmount(originalTxnId);
        return original.getAmount().subtract(alreadyRefunded);
    }

    // ── Generate billing schedule for a policy ────────────────────────────────

    @Transactional
    public List<BillingSchedule> generateBillingSchedule(UUID policyId, String policyNumber,
                                                          UUID customerId, UUID companyId,
                                                          BigDecimal premiumAmount, String frequency,
                                                          LocalDate startDate, int termYears) {
        int installmentsPerYear = switch (frequency) {
            case "MONTHLY"     -> 12;
            case "QUARTERLY"   -> 4;
            case "HALF_YEARLY" -> 2;
            default            -> 1;
        };

        List<BillingSchedule> schedule = new ArrayList<>();
        LocalDate dueDate = startDate;
        int totalInstallments = installmentsPerYear * termYears;

        for (int i = 1; i <= totalInstallments; i++) {
            schedule.add(BillingSchedule.builder()
                    .policyId(policyId)
                    .policyNumber(policyNumber)
                    .customerId(customerId)
                    .companyId(companyId)
                    .dueDate(dueDate)
                    .amount(premiumAmount)
                    .installmentNo(i)
                    .status(BillingStatus.PENDING)
                    .build());
            dueDate = advanceDueDate(dueDate, frequency);
        }

        return billingRepository.saveAll(schedule);
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    public PaymentResponse getById(UUID id) {
        return PaymentResponse.from(findOrThrow(id));
    }

    public PaymentResponse getByTransactionNumber(String txnNumber) {
        return PaymentResponse.from(paymentRepository
                .findByTransactionNumberAndDeletedFalse(txnNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Transaction not found: " + txnNumber)));
    }

    public PagedResponse<PaymentResponse> listByPolicy(UUID policyId, int page, int size) {
        Pageable p = PageRequest.of(page, Math.min(size, 100), Sort.by("createdAt").descending());
        return PagedResponse.of(paymentRepository.findByPolicyIdAndDeletedFalse(policyId, p)
                .map(PaymentResponse::from));
    }

    public PagedResponse<PaymentResponse> listByCompany(UUID companyId, PaymentStatus status,
                                                        TransactionType transactionType,
                                                        LocalDate dateFrom, LocalDate dateTo,
                                                        int page, int size, String sortParam) {
        Pageable p = buildPageable(page, size, sortParam);

        org.springframework.data.jpa.domain.Specification<PaymentTransaction> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("companyId"), companyId));
            predicates.add(cb.isFalse(root.get("deleted")));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (transactionType != null) predicates.add(cb.equal(root.get("transactionType"), transactionType));
            // Compare as full ZonedDateTime ranges rather than casting the timestamp
            // column to LocalDate — a JPA criteria .as(LocalDate.class) cast on a
            // ZonedDateTime path is provider-dependent and not reliably portable.
            if (dateFrom != null) predicates.add(cb.greaterThanOrEqualTo(
                    root.get("createdAt"), dateFrom.atStartOfDay(java.time.ZoneOffset.UTC)));
            if (dateTo != null) predicates.add(cb.lessThan(
                    root.get("createdAt"), dateTo.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC)));
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        Page<PaymentTransaction> result = paymentRepository.findAll(spec, p);
        return PagedResponse.of(result.map(PaymentResponse::from));
    }

    private Pageable buildPageable(int page, int size, String sortParam) {
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
        if (sortParam != null && sortParam.contains(",")) {
            String[] parts = sortParam.split(",", 2);
            Sort.Direction dir = parts[1].trim().equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
            sort = Sort.by(dir, parts[0].trim());
        }
        return PageRequest.of(page, Math.min(size, 100), sort);
    }

    public BigDecimal getTotalPaidPremium(UUID policyId) {
        BigDecimal total = paymentRepository.sumPaidPremiumsByPolicy(policyId);
        return total != null ? total : BigDecimal.ZERO;
    }

    // ── Scheduled: mark overdue billing entries ───────────────────────────────

    @Transactional
    public void processOverdueBilling() {
        List<BillingSchedule> overdue = billingRepository.findOverdue(LocalDate.now().minusDays(1));
        for (BillingSchedule b : overdue) {
            b.setStatus(BillingStatus.OVERDUE);
            billingRepository.save(b);
            // Publish event so notification-service can alert customer
            PaymentTransaction fakeTxn = PaymentTransaction.builder()
                    .id(UUID.randomUUID())
                    .transactionNumber("OVERDUE-" + b.getPolicyNumber())
                    .policyId(b.getPolicyId())
                    .policyNumber(b.getPolicyNumber())
                    .customerId(b.getCustomerId())
                    .companyId(b.getCompanyId())
                    .amount(b.getAmount())
                    .currency("INR")
                    .premiumDueDate(b.getDueDate())
                    .build();
            eventPublisher.publishPremiumOverdue(fakeTxn);
        }
        log.info("Marked {} billing entries as overdue", overdue.size());
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private PaymentTransaction findOrThrow(UUID id) {
        return paymentRepository.findById(id)
                .filter(t -> !t.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Payment transaction not found: " + id));
    }

    private void markBillingPaid(PaymentTransaction txn) {
        if (txn.getPremiumPeriodFrom() == null) return;
        billingRepository.findByPolicyIdOrderByDueDateAsc(txn.getPolicyId())
                .stream()
                .filter(b -> b.getStatus() == BillingStatus.PENDING || b.getStatus() == BillingStatus.OVERDUE)
                .findFirst()
                .ifPresent(b -> {
                    b.setStatus(BillingStatus.PAID);
                    b.setPaidAt(ZonedDateTime.now());
                    b.setTransaction(txn);
                    billingRepository.save(b);
                });
    }

    private String generateTransactionNumber(String prefix) {
        String txn = "TXN-" + prefix.toUpperCase() + "-" + System.currentTimeMillis();
        while (paymentRepository.existsByTransactionNumber(txn)) {
            txn = "TXN-" + prefix.toUpperCase() + "-" + System.currentTimeMillis();
        }
        return txn;
    }

    /**
     * AUDIT FINDING (§4.10 / §8): this used to silently return a
     * fabricated order ID ("order_DEV_" + timestamp) whenever Razorpay
     * wasn't configured, which then flowed straight into the API response
     * as if it were real — a fake-success path, and one that also
     * contradicts the platform's stated business model (this app records
     * payments made customer→insurer, it isn't necessarily an online
     * payment processor itself). Rather than silently degrading into fake
     * behavior, this now fails loudly and clearly when unconfigured — the
     * real Razorpay call path is untouched for when it IS configured.
     */
    private String simulateOrCallRazorpay(Map<String, Object> params) {
        if (razorpayKeyId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Online payment gateway is not configured (RAZORPAY_KEY_ID unset). "
                    + "This deployment can still record payments made outside this app via "
                    + "POST /payments/manual. See MONOLITH-CONVERSION-NOTES.md / the audit report §4.10 "
                    + "and §8 for the KEEP/ISOLATE/DEPRECATE decision this flow needs before enabling it.");
        }
        // Production: use Razorpay Java SDK here
        // RazorpayClient client = new RazorpayClient(razorpayKeyId, razorpayKeySecret);
        // Order order = client.orders.create(new JSONObject(params));
        // return order.get("id");
        return "order_" + System.currentTimeMillis();
    }

    /**
     * AUDIT FINDING (§4.10): previously "if (razorpayKeySecret.isBlank())
     * return true" — meant ANY payment verification succeeded, with ANY
     * signature (or none), whenever Razorpay wasn't configured. That's a
     * forgeable payment-confirmation hole, not just a fake-success
     * cosmetic issue. Now fails closed: no secret configured means no
     * signature can be verified, so verification fails rather than
     * silently passing.
     */
    private boolean verifyRazorpaySignature(String orderId, String paymentId, String signature) {
        try {
            if (razorpayKeySecret.isBlank()) return false; // cannot verify without a secret — fail closed
            String payload = orderId + "|" + paymentId;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(razorpayKeySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString().equals(signature);
        } catch (Exception e) {
            log.error("Signature verification error: {}", e.getMessage());
            return false;
        }
    }

    /**
     * AUDIT FINDING (§4.10): same fail-closed fix as verifyRazorpaySignature
     * — this endpoint is public/unauthenticated by design (it's called by
     * Razorpay, not a logged-in user), so an unconfigured webhook secret
     * previously meant literally anyone on the internet could POST a
     * fabricated webhook payload and have it accepted as genuine.
     */
    private boolean verifyWebhookSignature(String rawBody, String signature) {
        try {
            if (razorpayWebhookSecret.isBlank()) return false; // fail closed
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(razorpayWebhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString().equals(signature);
        } catch (Exception e) {
            return false;
        }
    }

    private void handlePaymentCaptured(Map<String, Object> payload) {
        try {
            Map<?, ?> paymentEntity = (Map<?, ?>) ((Map<?, ?>) payload.get("payment")).get("entity");
            String receipt = (String) paymentEntity.get("receipt");
            String gatewayPaymentId = (String) paymentEntity.get("id");
            String gatewayOrderId = (String) paymentEntity.get("order_id");

            paymentRepository.findByTransactionNumberAndDeletedFalse(receipt).ifPresent(txn -> {
                txn.setStatus(PaymentStatus.SUCCESS);
                txn.setGatewayPaymentId(gatewayPaymentId);
                txn.setProcessedAt(ZonedDateTime.now());
                paymentRepository.save(txn);
                markBillingPaid(txn);
                eventPublisher.publishPaymentSuccess(txn);
                log.info("Webhook: payment captured for {}", receipt);
            });
        } catch (Exception e) {
            log.error("Failed to handle payment.captured webhook: {}", e.getMessage());
        }
    }

    private void handlePaymentFailed(Map<String, Object> payload) {
        try {
            Map<?, ?> payment = (Map<?, ?>) payload.get("payment");

            if (payment == null) {
                log.warn("Webhook payment.failed: payment object is missing");
                return;
            }

            Map<?, ?> paymentEntity = (Map<?, ?>) payment.get("entity");

            if (paymentEntity == null) {
                log.warn("Webhook payment.failed: payment entity is missing");
                return;
            }

            String receipt = (String) paymentEntity.get("receipt");
            String errorDesc = (String) paymentEntity.get("error_description");

            if (receipt == null || receipt.isBlank()) {
                log.warn("Webhook payment.failed: receipt is missing");
                return;
            }

            if (errorDesc == null || errorDesc.isBlank()) {
                errorDesc = "Payment failed";
            }

            final String finalErrorDesc = errorDesc;

            paymentRepository
                    .findByTransactionNumberAndDeletedFalse(receipt)
                    .ifPresent(txn -> {

                        txn.setStatus(PaymentStatus.FAILED);
                        txn.setFailureReason(finalErrorDesc);

                        paymentRepository.save(txn);

                        eventPublisher.publishPaymentFailed(txn);

                        log.info(
                                "Webhook: payment failed for {}",
                                receipt
                        );
                    });

        } catch (Exception e) {

            log.error(
                    "Failed to handle payment.failed webhook: {}",
                    e.getMessage(),
                    e
            );
        }
    }

    private void handleRefundCreated(Map<String, Object> payload) {
        log.info("Refund created webhook received");
    }

    private LocalDate advanceDueDate(LocalDate date, String frequency) {
        return switch (frequency) {
            case "MONTHLY"     -> date.plusMonths(1);
            case "QUARTERLY"   -> date.plusMonths(3);
            case "HALF_YEARLY" -> date.plusMonths(6);
            default            -> date.plusYears(1);
        };
    }
}
