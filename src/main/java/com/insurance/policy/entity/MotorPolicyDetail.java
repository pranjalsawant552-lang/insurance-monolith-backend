package com.insurance.policy.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "motor_policy_details")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MotorPolicyDetail {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "policy_id") private Policy policy;
    @Column(name = "vehicle_number")   private String  vehicleNumber;
    @Column(name = "vehicle_make")     private String  vehicleMake;
    @Column(name = "vehicle_model")    private String  vehicleModel;
    @Column(name = "vehicle_year")     private Integer vehicleYear;
    @Column(name = "vehicle_type")     private String  vehicleType;
    @Column(name = "engine_number")    private String  engineNumber;
    @Column(name = "chassis_number")   private String  chassisNumber;
    @Column(name = "cubic_capacity")   private Integer cubicCapacity;
    @Column(name = "seating_capacity") private Integer seatingCapacity;
    @Column(name = "idv_value", precision = 12, scale = 2) private BigDecimal idvValue;
    @Column(name = "policy_type")      private String policyType;
    @Column(name = "ncb_percent", precision = 5, scale = 2)
    @Builder.Default private BigDecimal ncbPercent = BigDecimal.ZERO;
    @Column(name = "garage_network_id") private UUID garageNetworkId;
}
