package com.insurance.product.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity @Table(name = "product_riders")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProductRider {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false) private InsuranceProduct product;
    @Column(nullable = false) private String name;
    @Column(nullable = false) private String code;
    @Column(columnDefinition = "TEXT") private String description;
    @Column(name = "additional_premium", nullable = false, precision = 10, scale = 2)
    private BigDecimal additionalPremium;
    @Builder.Default @Column(name = "is_active") private boolean active = true;
}
