package com.insurance.product.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "insurance_products")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InsuranceProduct {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true) private String code;
    @Column(nullable = false) private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "insurance_type", nullable = false)
    private InsuranceType insuranceType;

    @Column private String category;
    @Column(columnDefinition = "TEXT") private String description;
    @Column(name = "key_features", columnDefinition = "TEXT") private String keyFeatures;

    @Column(name = "base_premium", nullable = false, precision = 12, scale = 2)
    private BigDecimal basePremium;

    @Column(name = "min_sum_assured", precision = 15, scale = 2) private BigDecimal minSumAssured;
    @Column(name = "max_sum_assured", precision = 15, scale = 2) private BigDecimal maxSumAssured;
    @Column(name = "min_age") private Integer minAge;
    @Column(name = "max_age") private Integer maxAge;
    @Column(name = "min_term_years") private Integer minTermYears;
    @Column(name = "max_term_years") private Integer maxTermYears;
    @Builder.Default @Column(name = "grace_period_days") private int gracePeriodDays = 30;
    @Builder.Default @Column(name = "free_look_days")    private int freeLookDays    = 15;

    @Builder.Default @Column(name = "is_active")   private boolean active   = true;
    @Builder.Default @Column(name = "is_featured") private boolean featured = false;

    @Builder.Default @Column(name = "current_version") private int currentVersion = 1;

    @Column(name = "company_id", nullable = false) private UUID companyId;
    @Column(name = "branch_id") private UUID branchId;
    @Column(name = "created_by", nullable = false) private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default private ZonedDateTime createdAt = ZonedDateTime.now();
    @Column(name = "updated_at", nullable = false)
    @Builder.Default private ZonedDateTime updatedAt = ZonedDateTime.now();

    @Column(name = "is_deleted") @Builder.Default private boolean deleted = false;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default private List<ProductRider> riders = new ArrayList<>();

    @PreUpdate public void preUpdate() { this.updatedAt = ZonedDateTime.now(); }
}
