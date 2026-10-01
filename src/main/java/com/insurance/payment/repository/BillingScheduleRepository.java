package com.insurance.payment.repository;

import com.insurance.payment.entity.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface BillingScheduleRepository extends JpaRepository<BillingSchedule, UUID> {

    List<BillingSchedule> findByPolicyIdOrderByDueDateAsc(UUID policyId);

    Page<BillingSchedule> findByCompanyIdAndStatusOrderByDueDateAsc(
            UUID companyId, BillingStatus status, Pageable pageable);

    @Query("SELECT b FROM BillingSchedule b WHERE b.status = 'PENDING' AND b.dueDate <= :cutoff")
    List<BillingSchedule> findOverdue(@Param("cutoff") LocalDate cutoff);

    @Query("SELECT b FROM BillingSchedule b WHERE b.status = 'PENDING' AND b.dueDate BETWEEN :from AND :to")
    List<BillingSchedule> findUpcoming(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
