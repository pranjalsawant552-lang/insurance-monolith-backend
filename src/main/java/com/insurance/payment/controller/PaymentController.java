package com.insurance.payment.controller;

import com.insurance.payment.dto.request.*;
import com.insurance.payment.dto.response.*;
import com.insurance.payment.entity.PaymentStatus;
import com.insurance.payment.service.IdempotencyService;
import com.insurance.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Payment processing, billing schedules, disbursements")
public class PaymentController {

    private final PaymentService paymentService;
    private final IdempotencyService idempotencyService;

    /** Replays a cached idempotent response verbatim — the raw JSON string is
     *  written through as-is rather than re-serialized, so the replayed byte
     *  content matches the original response exactly. */
    private ResponseEntity<Object> replay(IdempotencyService.CachedResponse cached) {
        return ResponseEntity.status(cached.status())
                .contentType(MediaType.APPLICATION_JSON)
                .body(cached.body());
    }

    // ── Online payment: create gateway order (Razorpay) ───────────────────────

    @PostMapping("/gateway/create-order")
    @PreAuthorize("hasAuthority('PAYMENT_WRITE')")
    @Operation(summary = "Create a Razorpay order to initiate online payment")
    public ResponseEntity<Object> createOrder(
            @Valid @RequestBody GatewayOrderRequest req,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request) {

        final String endpoint = "POST /payments/gateway/create-order";
        var cached = idempotencyService.checkExisting(idempotencyKey, endpoint, req);
        if (cached.isPresent()) return replay(cached.get());

        Map<String, Object> order = paymentService.createGatewayOrder(
                req, extractCompanyId(request), extractUserId(request));
        ApiResponse<Map<String, Object>> body = ApiResponse.success(order, "Gateway order created.");
        idempotencyService.store(idempotencyKey, endpoint, req, HttpStatus.OK.value(), body);
        return ResponseEntity.ok(body);
    }

    // ── Online payment: verify after customer completes payment ───────────────

    @PostMapping("/gateway/verify")
    @PreAuthorize("hasAuthority('PAYMENT_WRITE')")
    @Operation(summary = "Verify Razorpay payment after frontend callback")
    public ResponseEntity<ApiResponse<PaymentResponse>> verifyPayment(
            @RequestParam String transactionId,
            @RequestParam String gatewayOrderId,
            @RequestParam String gatewayPaymentId,
            @RequestParam String gatewaySignature) {
        PaymentResponse resp = paymentService.verifyAndCapturePayment(
                transactionId, gatewayOrderId, gatewayPaymentId, gatewaySignature);
        return ResponseEntity.ok(ApiResponse.success(resp, "Payment verified successfully."));
    }

    // ── Razorpay webhook ──────────────────────────────────────────────────────

    @PostMapping("/webhook/razorpay")
    @Operation(summary = "Razorpay webhook endpoint — do not call manually")
    public ResponseEntity<Void> razorpayWebhook(
            @RequestBody RazorpayWebhookRequest payload,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature,
            @RequestBody(required = false) String rawBody) {
        paymentService.handleRazorpayWebhook(payload, signature, rawBody != null ? rawBody : "");
        return ResponseEntity.ok().build();
    }

    // ── Manual / cash / cheque payment recording ──────────────────────────────

    @PostMapping("/manual")
    @PreAuthorize("hasAuthority('PAYMENT_WRITE')")
    @Operation(summary = "Record an offline payment (cash, cheque, NEFT)")
    public ResponseEntity<Object> recordManual(
            @Valid @RequestBody CreatePaymentRequest req,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request) {

        final String endpoint = "POST /payments/manual";
        var cached = idempotencyService.checkExisting(idempotencyKey, endpoint, req);
        if (cached.isPresent()) return replay(cached.get());

        PaymentResponse resp = paymentService.recordManualPayment(
                req, extractCompanyId(request), extractUserId(request));
        ApiResponse<PaymentResponse> body = ApiResponse.success(resp, "Payment recorded successfully.");
        idempotencyService.store(idempotencyKey, endpoint, req, HttpStatus.CREATED.value(), body);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    // ── Claim settlement disbursement ─────────────────────────────────────────

    @PostMapping("/claim-settlement")
    @PreAuthorize("hasAuthority('PAYMENT_DISBURSE')")
    @Operation(summary = "Initiate claim settlement disbursement via NEFT")
    public ResponseEntity<Object> disburse(
            @Valid @RequestBody CreatePaymentRequest req,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request) {

        final String endpoint = "POST /payments/claim-settlement";
        var cached = idempotencyService.checkExisting(idempotencyKey, endpoint, req);
        if (cached.isPresent()) return replay(cached.get());

        PaymentResponse resp = paymentService.disburseClaimSettlement(
                req, extractCompanyId(request), extractUserId(request));
        ApiResponse<PaymentResponse> body = ApiResponse.success(resp, "Claim settlement initiated.");
        idempotencyService.store(idempotencyKey, endpoint, req, HttpStatus.CREATED.value(), body);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PostMapping("/{id}/confirm-disbursement")
    @PreAuthorize("hasAuthority('PAYMENT_DISBURSE')")
    @Operation(summary = "Confirm settlement with UTR number after bank transfer")
    public ResponseEntity<Object> confirmDisbursement(
            @PathVariable UUID id,
            @RequestParam String utrNumber,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request) {

        final String endpoint = "POST /payments/{id}/confirm-disbursement";
        // Hash the whole logical operation (which payment + which UTR), not just
        // the path variable — a caller confirming a different payment or a
        // different UTR under the same key should hit the CONFLICT path, not a
        // false-positive replay.
        record ConfirmPayload(UUID id, String utrNumber) {}
        var cached = idempotencyService.checkExisting(idempotencyKey, endpoint, new ConfirmPayload(id, utrNumber));
        if (cached.isPresent()) return replay(cached.get());

        PaymentResponse resp = paymentService.confirmDisbursement(id, utrNumber, extractUserId(request));
        ApiResponse<PaymentResponse> body = ApiResponse.success(resp, "Disbursement confirmed.");
        idempotencyService.store(idempotencyKey, endpoint, new ConfirmPayload(id, utrNumber), HttpStatus.OK.value(), body);
        return ResponseEntity.ok(body);
    }

    // ── Refund (full or partial) ────────────────────────────────────────────

    @PostMapping("/{id}/refund")
    @PreAuthorize("hasAuthority('PAYMENT_REFUND')")
    @Operation(summary = "Issue a full or partial refund against a successful transaction")
    public ResponseEntity<Object> refund(
            @PathVariable UUID id,
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) String reason,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request) {

        final String endpoint = "POST /payments/{id}/refund";
        record RefundPayload(UUID id, BigDecimal amount, String reason) {}
        var payload = new RefundPayload(id, amount, reason);
        var cached = idempotencyService.checkExisting(idempotencyKey, endpoint, payload);
        if (cached.isPresent()) return replay(cached.get());

        PaymentResponse resp = paymentService.refund(id, amount, reason, extractUserId(request));
        ApiResponse<PaymentResponse> body = ApiResponse.success(resp, "Refund issued.");
        idempotencyService.store(idempotencyKey, endpoint, payload, HttpStatus.OK.value(), body);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/{id}/refundable-balance")
    @PreAuthorize("hasAuthority('PAYMENT_READ')")
    @Operation(summary = "Get the remaining refundable balance on a transaction")
    public ResponseEntity<ApiResponse<BigDecimal>> refundableBalance(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(paymentService.getRefundableBalance(id)));
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PAYMENT_READ')")
    @Operation(summary = "Get transaction by ID")
    public ResponseEntity<ApiResponse<PaymentResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(paymentService.getById(id)));
    }

    @GetMapping("/number/{txnNumber}")
    @PreAuthorize("hasAuthority('PAYMENT_READ')")
    @Operation(summary = "Get transaction by transaction number")
    public ResponseEntity<ApiResponse<PaymentResponse>> getByNumber(@PathVariable String txnNumber) {
        return ResponseEntity.ok(ApiResponse.success(paymentService.getByTransactionNumber(txnNumber)));
    }

    // ── List (paginated) ──────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasAuthority('PAYMENT_READ')")
    @Operation(summary = "List all transactions for company (paginated, sortable, filterable by status/type/date range)")
    public ResponseEntity<ApiResponse<PagedResponse<PaymentResponse>>> list(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) com.insurance.payment.entity.TransactionType transactionType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate dateTo,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                paymentService.listByCompany(extractCompanyId(request), status, transactionType,
                        dateFrom, dateTo, page, size, sort)));
    }

    @GetMapping("/policy/{policyId}")
    @PreAuthorize("hasAuthority('PAYMENT_READ')")
    @Operation(summary = "List all transactions for a specific policy")
    public ResponseEntity<ApiResponse<PagedResponse<PaymentResponse>>> listByPolicy(
            @PathVariable UUID policyId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                paymentService.listByPolicy(policyId, page, size)));
    }

    @GetMapping("/policy/{policyId}/total-paid")
    @PreAuthorize("hasAuthority('PAYMENT_READ')")
    @Operation(summary = "Get total premium paid for a policy")
    public ResponseEntity<ApiResponse<BigDecimal>> totalPaid(@PathVariable UUID policyId) {
        return ResponseEntity.ok(ApiResponse.success(
                paymentService.getTotalPaidPremium(policyId),
                "Total premium paid (successful transactions only)."));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UUID extractUserId(HttpServletRequest req) {
        Object id = req.getAttribute("userId");
        if (id == null) throw new org.springframework.web.server.ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "Missing user context");
        return UUID.fromString(id.toString());
    }
    private UUID extractCompanyId(HttpServletRequest req) {
        Object id = req.getAttribute("companyId");
        if (id == null) throw new org.springframework.web.server.ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "Missing company context");
        return UUID.fromString(id.toString());
    }
}
