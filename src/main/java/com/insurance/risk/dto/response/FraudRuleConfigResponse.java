package com.insurance.risk.dto.response;

import com.insurance.risk.entity.FraudRuleConfig;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class FraudRuleConfigResponse {
    private UUID id;
    private String ruleType;
    private Integer thresholdDays;
    private BigDecimal weight;
    private String description;
    private boolean active;

    public static FraudRuleConfigResponse from(FraudRuleConfig r) {
        return FraudRuleConfigResponse.builder()
                .id(r.getId())
                .ruleType(r.getRuleType().name())
                .thresholdDays(r.getThresholdDays())
                .weight(r.getWeight())
                .description(r.getDescription())
                .active(r.isActive())
                .build();
    }
}
