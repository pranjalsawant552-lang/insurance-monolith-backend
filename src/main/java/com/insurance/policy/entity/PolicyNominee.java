package com.insurance.policy.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "policy_nominees")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PolicyNominee {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "policy_id") private Policy policy;
    @Column(name = "full_name", nullable = false) private String fullName;
    @Column(nullable = false) private String relationship;
    @Column(name = "date_of_birth") private LocalDate dateOfBirth;
    @Column(name = "share_percent", precision = 5, scale = 2)
    @Builder.Default private BigDecimal sharePercent = BigDecimal.valueOf(100);
    @Column(name = "is_primary") @Builder.Default private boolean primary = false;
    private String mobile;
    private String email;
    private String address;
    @Column(name = "created_at", updatable = false)
    @Builder.Default private ZonedDateTime createdAt = ZonedDateTime.now();
}
