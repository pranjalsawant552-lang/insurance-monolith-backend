package com.insurance.policy.entity;

public enum PolicyStatus {
    PENDING,        // Application submitted, awaiting risk assessment
    UNDERWRITING,   // Risk score requires manual underwriter review before issuance
    ACTIVE,         // Fully issued and in force
    LAPSED,         // Premium not paid after grace period
    SURRENDERED,    // Customer surrendered the policy
    MATURED,        // Policy term completed
    CANCELLED,      // Cancelled within free-look period (or declined by an underwriter)
    CLAIMED         // Death/total-loss claim paid and policy closed
}
