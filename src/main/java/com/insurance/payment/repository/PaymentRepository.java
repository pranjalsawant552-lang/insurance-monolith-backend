package com.insurance.payment.repository;

import com.insurance.payment.entity.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<PaymentTransaction, UUID>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<PaymentTransaction> {

    Optional<PaymentTransaction> findByTransactionNumberAndDeletedFalse(String txnNumber);
    Optional<PaymentTransaction> findByGatewayPaymentIdAndDeletedFalse(String gatewayPaymentId);

    Page<PaymentTransaction> findByPolicyIdAndDeletedFalse(UUID policyId, Pageable pageable);
    Page<PaymentTransaction> findByCustomerIdAndDeletedFalse(UUID customerId, Pageable pageable);
    Page<PaymentTransaction> findByCompanyIdAndDeletedFalse(UUID companyId, Pageable pageable);
    Page<PaymentTransaction> findByCompanyIdAndStatusAndDeletedFalse(UUID companyId, PaymentStatus status, Pageable pageable);

    boolean existsByTransactionNumber(String txnNumber);

    @Query("SELECT SUM(p.amount) FROM PaymentTransaction p WHERE p.policyId = :pid AND p.status = 'SUCCESS' AND p.transactionType = 'PREMIUM'")
    java.math.BigDecimal sumPaidPremiumsByPolicy(@Param("pid") UUID policyId);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM PaymentTransaction p WHERE p.originalTransactionId = :originalId AND p.status = 'SUCCESS' AND p.transactionType = 'REFUND'")
    java.math.BigDecimal sumRefundedAmount(@Param("originalId") UUID originalId);
}
