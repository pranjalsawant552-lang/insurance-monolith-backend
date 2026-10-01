package com.insurance.customer.service;

import com.insurance.customer.dto.request.CreateCustomerRequest;
import com.insurance.customer.dto.response.*;
import com.insurance.customer.entity.*;
import com.insurance.customer.event.CustomerEventPublisher;
import com.insurance.customer.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZonedDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerEventPublisher eventPublisher;
    private final com.insurance.customer.event.AuditEventPublisher auditEventPublisher;

    // ── Create ────────────────────────────────────────────────────────────────

    @Transactional
    public CustomerResponse create(CreateCustomerRequest req, UUID createdBy,
                                   UUID companyId, UUID branchId) {
        if (customerRepository.existsByEmailAndDeletedFalse(req.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A customer with this email already exists.");
        }
        if (customerRepository.existsByMobileNumberAndDeletedFalse(req.getMobileNumber())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A customer with this mobile number already exists.");
        }

        String customerCode = generateCustomerCode(companyId);

        Customer customer = Customer.builder()
                .customerCode(customerCode)
                .firstName(req.getFirstName().trim())
                .lastName(req.getLastName().trim())
                .dateOfBirth(req.getDateOfBirth())
                .gender(req.getGender())
                .maritalStatus(req.getMaritalStatus())
                .occupation(req.getOccupation())
                .annualIncome(req.getAnnualIncome())
                .email(req.getEmail().toLowerCase().trim())
                .mobileNumber(req.getMobileNumber().trim())
                .alternateMobile(req.getAlternateMobile())
                .panNumber(req.getPanNumber())
                .aadhaarNumber(req.getAadhaarNumber())
                .passportNumber(req.getPassportNumber())
                .bloodGroup(req.getBloodGroup())
                .heightCm(req.getHeightCm())
                .weightKg(req.getWeightKg())
                .smoker(req.getSmoker() != null ? req.getSmoker() : false)
                .preExistingConditions(req.getPreExistingConditions())
                .companyId(companyId)
                .branchId(branchId)
                .kycStatus(KycStatus.PENDING)
                .createdBy(createdBy)
                .build();

        // Add addresses
        if (req.getAddresses() != null) {
            req.getAddresses().forEach(a -> {
                CustomerAddress addr = CustomerAddress.builder()
                        .customer(customer)
                        .addressType(a.getAddressType())
                        .addressLine1(a.getAddressLine1())
                        .addressLine2(a.getAddressLine2())
                        .city(a.getCity())
                        .state(a.getState())
                        .pincode(a.getPincode())
                        .country(a.getCountry() != null ? a.getCountry() : "India")
                        .primary(a.isPrimary())
                        .build();
                customer.getAddresses().add(addr);
            });
        }

        // Default preferences
        CustomerPreference prefs = CustomerPreference.builder().customer(customer).build();
        customer.setPreferences(prefs);

        Customer saved = customerRepository.save(customer);
        eventPublisher.publishCustomerCreated(saved);
        auditEventPublisher.publish("CUSTOMER", saved.getId().toString(), "CREATE",
                null,
                java.util.Map.of("customerCode", saved.getCustomerCode(), "kycStatus", "PENDING",
                        "email", saved.getEmail(), "mobileNumber", saved.getMobileNumber()),
                "Customer " + saved.getCustomerCode() + " created");
        // NOTE: PAN/Aadhaar/passport are deliberately excluded from the audit payload above —
        // they're encrypted at rest for a reason, and the audit trail must not become a second,
        // less-protected copy of the same PII. Which fields changed matters more here than values.
        log.info("Customer created: {} [{}]", saved.getCustomerCode(), saved.getId());
        return CustomerResponse.from(saved);
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    /**
     * INTERNAL USE ONLY — no tenant check. Callable only from other
     * in-process modules reacting to our own domain events (e.g.
     * notification's CustomerServiceClient resolving contact details for a
     * policy/claim/payment event it just received), never from a
     * controller reachable by an external HTTP request. The customerId in
     * that case comes from an event payload this application itself
     * published, not from untrusted client input, so there's no caller to
     * scope against. Do not add a controller endpoint that calls this.
     */
    public CustomerResponse getByIdInternal(UUID id) {
        return CustomerResponse.from(customerRepository.findById(id)
                .filter(c -> !c.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Customer not found: " + id)));
    }

    /**
     * SECURITY FIX: previously getById(UUID id) took no companyId and
     * returned any customer in the database regardless of which company the
     * caller belonged to — a cross-tenant IDOR (see the audit report, §7.1).
     * Now requires the caller's company to match the record's, and returns
     * 404 rather than 403 on a mismatch so a caller probing IDs can't tell
     * the difference between "doesn't exist" and "exists, not yours."
     */
    public CustomerResponse getById(UUID id, UUID companyId) {
        return CustomerResponse.from(findOrThrow(id, companyId));
    }

    public CustomerResponse getByCode(String code, UUID companyId) {
        Customer customer = customerRepository.findByCustomerCodeAndDeletedFalse(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Customer not found: " + code));
        if (!customer.getCompanyId().equals(companyId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found: " + code);
        }
        return CustomerResponse.from(customer);
    }

    // ── List / Search ─────────────────────────────────────────────────────────

    public PagedResponse<CustomerResponse> list(UUID companyId, KycStatus kycStatus,
                                                int page, int size, String sort) {
        Pageable pageable = buildPageable(page, size, sort);
        Page<Customer> result = kycStatus != null
                ? customerRepository.findByCompanyIdAndKycStatusAndDeletedFalse(companyId, kycStatus, pageable)
                : customerRepository.findByCompanyIdAndDeletedFalse(companyId, pageable);
        return PagedResponse.of(result.map(CustomerResponse::from));
    }

    public PagedResponse<CustomerResponse> search(UUID companyId, String query,
                                                  int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100),
                Sort.by("createdAt").descending());
        return PagedResponse.of(customerRepository.search(companyId, query, pageable)
                .map(CustomerResponse::from));
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @Transactional
    public CustomerResponse update(UUID id, UUID companyId, CreateCustomerRequest req, UUID updatedBy) {
        Customer customer = findOrThrow(id, companyId);
        if (req.getFirstName() != null) customer.setFirstName(req.getFirstName().trim());
        if (req.getLastName() != null)  customer.setLastName(req.getLastName().trim());
        if (req.getOccupation() != null) customer.setOccupation(req.getOccupation());
        if (req.getAnnualIncome() != null) customer.setAnnualIncome(req.getAnnualIncome());
        if (req.getAlternateMobile() != null) customer.setAlternateMobile(req.getAlternateMobile());
        if (req.getBloodGroup() != null) customer.setBloodGroup(req.getBloodGroup());
        if (req.getHeightCm() != null) customer.setHeightCm(req.getHeightCm());
        if (req.getWeightKg() != null) customer.setWeightKg(req.getWeightKg());
        if (req.getSmoker() != null)   customer.setSmoker(req.getSmoker());
        if (req.getPreExistingConditions() != null)
            customer.setPreExistingConditions(req.getPreExistingConditions());
        customer.setUpdatedBy(updatedBy);
        Customer saved = customerRepository.save(customer);
        auditEventPublisher.publish("CUSTOMER", saved.getId().toString(), "UPDATE",
                null, null,
                "Customer " + saved.getCustomerCode() + " profile updated");
        return CustomerResponse.from(saved);
    }

    // ── KYC Workflow ──────────────────────────────────────────────────────────

    @Transactional
    public CustomerResponse submitForKycReview(UUID id, UUID companyId, UUID submittedBy) {
        Customer customer = findOrThrow(id, companyId);
        if (customer.getKycStatus() != KycStatus.PENDING &&
                customer.getKycStatus() != KycStatus.REJECTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Customer KYC is already " + customer.getKycStatus());
        }
        if (customer.getKycDocuments().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Please upload at least one KYC document before submitting for review.");
        }
        customer.setKycStatus(KycStatus.IN_REVIEW);
        customer.setUpdatedBy(submittedBy);
        Customer saved = customerRepository.save(customer);
        eventPublisher.publishKycSubmitted(saved);
        return CustomerResponse.from(saved);
    }

    @Transactional
    public CustomerResponse approveKyc(UUID id, UUID companyId, UUID approvedBy) {
        Customer customer = findOrThrow(id, companyId);
        if (customer.getKycStatus() != KycStatus.IN_REVIEW) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only IN_REVIEW customers can be KYC-approved.");
        }
        customer.setKycStatus(KycStatus.VERIFIED);
        customer.setKycVerifiedAt(ZonedDateTime.now());
        customer.setKycVerifiedBy(approvedBy);
        customer.setKycRejectionReason(null);
        customer.setUpdatedBy(approvedBy);
        Customer saved = customerRepository.save(customer);
        eventPublisher.publishKycVerified(saved);
        auditEventPublisher.publish("CUSTOMER", saved.getId().toString(), "KYC_APPROVE",
                java.util.Map.of("kycStatus", "IN_REVIEW"),
                java.util.Map.of("kycStatus", "VERIFIED"),
                "KYC approved for customer " + saved.getCustomerCode());
        log.info("KYC approved for customer {}", customer.getCustomerCode());
        return CustomerResponse.from(saved);
    }

    @Transactional
    public CustomerResponse rejectKyc(UUID id, UUID companyId, String reason, UUID rejectedBy) {
        Customer customer = findOrThrow(id, companyId);
        if (customer.getKycStatus() != KycStatus.IN_REVIEW) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only IN_REVIEW customers can be KYC-rejected.");
        }
        customer.setKycStatus(KycStatus.REJECTED);
        customer.setKycRejectionReason(reason);
        customer.setUpdatedBy(rejectedBy);
        Customer saved = customerRepository.save(customer);
        eventPublisher.publishKycRejected(saved, reason);
        auditEventPublisher.publish("CUSTOMER", saved.getId().toString(), "KYC_REJECT",
                java.util.Map.of("kycStatus", "IN_REVIEW"),
                java.util.Map.of("kycStatus", "REJECTED", "reason", reason != null ? reason : ""),
                "KYC rejected for customer " + saved.getCustomerCode() + ": " + (reason != null ? reason : "no reason given"));
        return CustomerResponse.from(saved);
    }

    // ── Soft Delete ───────────────────────────────────────────────────────────

    @Transactional
    public void delete(UUID id, UUID companyId, UUID deletedBy) {
        Customer customer = findOrThrow(id, companyId);
        customer.setDeleted(true);
        customer.setDeletedAt(ZonedDateTime.now());
        customer.setDeletedBy(deletedBy);
        customerRepository.save(customer);
        auditEventPublisher.publish("CUSTOMER", customer.getId().toString(), "DELETE",
                java.util.Map.of("deleted", "false"),
                java.util.Map.of("deleted", "true"),
                "Customer " + customer.getCustomerCode() + " soft-deleted");
        log.info("Customer soft-deleted: {}", customer.getCustomerCode());
    }

    // ── Link user account ─────────────────────────────────────────────────────

    @Transactional
    public void linkUserAccount(UUID customerId, UUID userId, UUID companyId) {

        Customer customer = findOrThrow(customerId, companyId);

        customer.setUserId(userId);

        customerRepository.save(customer);
    }

    // ── Stats ─────────────────────────────────────────────────────────────────

    public CustomerStatsResponse getStats(UUID companyId) {
        return CustomerStatsResponse.builder()
                .totalCustomers(customerRepository.countByCompanyIdAndKycStatusAndDeletedFalse(
                        companyId, KycStatus.VERIFIED)
                        + customerRepository.countByCompanyIdAndKycStatusAndDeletedFalse(
                        companyId, KycStatus.PENDING)
                        + customerRepository.countByCompanyIdAndKycStatusAndDeletedFalse(
                        companyId, KycStatus.IN_REVIEW)
                        + customerRepository.countByCompanyIdAndKycStatusAndDeletedFalse(
                        companyId, KycStatus.REJECTED))
                .verifiedCustomers(customerRepository.countByCompanyIdAndKycStatusAndDeletedFalse(
                        companyId, KycStatus.VERIFIED))
                .pendingKyc(customerRepository.countByCompanyIdAndKycStatusAndDeletedFalse(
                        companyId, KycStatus.PENDING))
                .inReviewKyc(customerRepository.countByCompanyIdAndKycStatusAndDeletedFalse(
                        companyId, KycStatus.IN_REVIEW))
                .rejectedKyc(customerRepository.countByCompanyIdAndKycStatusAndDeletedFalse(
                        companyId, KycStatus.REJECTED))
                .build();
    }

    // ── Kafka consumer: user registered in auth-service ───────────────────────

    @Transactional
    public void handleUserRegistered(UUID userId, String email, String firstName,
                                     String lastName, String mobileNumber) {
        // Auto-create a minimal customer profile when a user self-registers
        if (customerRepository.findByUserIdAndDeletedFalse(userId).isPresent()) return;

        String code = generateCustomerCode(null);
        Customer customer = Customer.builder()
                .userId(userId)
                .customerCode(code)
                .firstName(firstName)
                .lastName(lastName)
                .dateOfBirth(java.time.LocalDate.of(1990, 1, 1)) // placeholder — must be updated
                .gender("UNKNOWN")
                .email(email.toLowerCase())
                .mobileNumber(mobileNumber)
                .companyId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
                .kycStatus(KycStatus.PENDING)
                .createdBy(userId)
                .build();
        CustomerPreference prefs = CustomerPreference.builder().customer(customer).build();
        customer.setPreferences(prefs);
        customerRepository.save(customer);
        log.info("Auto-created customer profile for user {}", userId);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Customer findOrThrow(UUID id, UUID companyId) {
        Customer customer = customerRepository.findById(id)
                .filter(c -> !c.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Customer not found: " + id));
        // SECURITY: 404, not 403 — don't confirm the record exists to a caller
        // outside its company. See audit report §7.1 / CustomerController.
        if (!customer.getCompanyId().equals(companyId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found: " + id);
        }
        return customer;
    }

    private String generateCustomerCode(UUID companyId) {
        String prefix = "CUST";
        String code = prefix + "-" + String.valueOf(System.currentTimeMillis()).substring(5);
        while (customerRepository.existsByCustomerCode(code)) {
            code = prefix + "-" + System.currentTimeMillis();
        }
        return code;
    }

    private Pageable buildPageable(int page, int size, String sort) {
        size = Math.min(size, 100);
        String[] parts = (sort != null ? sort : "createdAt,desc").split(",");
        Sort s = parts.length > 1 && "asc".equalsIgnoreCase(parts[1])
                ? Sort.by(parts[0]).ascending()
                : Sort.by(parts[0]).descending();
        return PageRequest.of(page, size, s);
    }
}
