package com.insurance.customer.dto.response;

import lombok.Builder;
import lombok.Data;

@Data @Builder
public class CustomerStatsResponse {
    private long totalCustomers;
    private long verifiedCustomers;
    private long pendingKyc;
    private long inReviewKyc;
    private long rejectedKyc;
}
