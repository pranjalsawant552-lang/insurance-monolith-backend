package com.insurance.customer.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;

// ── Address ───────────────────────────────────────────────────────────────────
@Entity @Table(name = "customer_addresses")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CustomerAddress {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "customer_id") private Customer customer;
    @Column(name = "address_type", nullable = false) private String addressType; // PERMANENT, CORRESPONDENCE, OFFICE
    @Column(name = "address_line1", nullable = false) private String addressLine1;
    @Column(name = "address_line2") private String addressLine2;
    @Column(nullable = false) private String city;
    @Column(nullable = false) private String state;
    @Column(nullable = false) private String pincode;
    @Builder.Default @Column private String country = "India";
    @Column(name = "is_primary") @Builder.Default private boolean primary = false;
    @Column(name = "created_at", updatable = false) @Builder.Default private ZonedDateTime createdAt = ZonedDateTime.now();
}
