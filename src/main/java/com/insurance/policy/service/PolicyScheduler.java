package com.insurance.policy.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PolicyScheduler {

    private final PolicyService policyService;

    // Every day at 01:00 — mark policies lapsed
    @Scheduled(cron = "0 0 1 * * *")
    public void lapseCheck() {
        log.info("Running daily lapse check...");
        policyService.processLapsedPolicies();
    }

    // Every day at 08:00 — send renewal reminders (30-day window)
    @Scheduled(cron = "0 0 8 * * *")
    public void renewalReminders() {
        log.info("Sending renewal reminders...");
        policyService.sendRenewalReminders();
    }
}
