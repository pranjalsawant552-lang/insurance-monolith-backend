package com.insurance.risk.dto.request;

import com.insurance.risk.entity.FraudRuleConfig;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class FraudRuleConfigRequest {
    @NotNull private FraudRuleConfig.RuleType ruleType;
    private Integer thresholdDays;
    @NotNull private BigDecimal weight;
    private String description;
    private boolean active = true;
}
