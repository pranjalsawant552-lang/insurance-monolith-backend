package com.insurance.policy.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "health_policy_details")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class HealthPolicyDetail {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "policy_id") private Policy policy;
    @Column(name = "plan_type") private String planType;
    @Column(name = "room_rent_limit", precision = 10, scale = 2) private BigDecimal roomRentLimit;
    @Column(name = "day_care_covered")  @Builder.Default private boolean dayCareCovered  = true;
    @Column(name = "maternity_covered") @Builder.Default private boolean maternityCovered = false;
    @Column(name = "pre_existing_wait") @Builder.Default private int     preExistingWait  = 48;
    @Column(name = "network_hospital_id") private UUID networkHospitalId;
}
