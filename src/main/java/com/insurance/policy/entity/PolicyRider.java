package com.insurance.policy.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "policy_riders")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PolicyRider {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "policy_id") private Policy policy;
    /**
     * MONOLITH CONVERSION NOTE: was @ManyToOne to this module's own local
     * ProductRider entity — a copy of product-service's real product_riders
     * table that policy-service's own V2 migration flagged as vestigial and
     * never populated (verified zero rows written by any service code).
     * That local copy is deleted entirely in the merged single-schema
     * database (it collided with product-service's real insurance_products/
     * product_riders tables). References product-service's ProductRider.id
     * directly now — no local FK, same pattern already used for
     * Policy.customerId/agentId.
     */
    @Column(name = "rider_id") private UUID riderId;
    @Column(precision = 10, scale = 2) private BigDecimal premium;
    @Column(name = "is_active") @Builder.Default private boolean active = true;
}
