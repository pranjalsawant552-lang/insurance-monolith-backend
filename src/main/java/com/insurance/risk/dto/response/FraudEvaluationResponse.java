package com.insurance.risk.dto.response;

import com.insurance.risk.entity.FraudEvaluation;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class FraudEvaluationResponse {
    private UUID id;
    private UUID claimId;
    private UUID policyId;
    private BigDecimal fraudScore;
    private List<Map<String, Object>> triggeredRules;
    private boolean recommendFlag;
    private ZonedDateTime createdAt;

    public static FraudEvaluationResponse from(FraudEvaluation e) {
        return FraudEvaluationResponse.builder()
                .id(e.getId())
                .claimId(e.getClaimId())
                .policyId(e.getPolicyId())
                .fraudScore(e.getFraudScore())
                .triggeredRules(e.getTriggeredRules())
                .recommendFlag(e.isRecommendFlag())
                .createdAt(e.getCreatedAt())
                .build();
    }
}
