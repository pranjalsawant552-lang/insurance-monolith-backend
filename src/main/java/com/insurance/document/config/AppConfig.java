package com.insurance.document.config;

import com.insurance.document.service.OcrService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// NOTE: The security bean that used to live here
// (a permissive "anyRequest().permitAll()" filter chain)
// was removed as part of Phase 1 RBAC enforcement.
//
// The real security configuration is provided by:
// com.insurance.document.config.SecurityConfig
//
// Security-related filtering is handled by:
// security/HeaderAuthFilter.java


/**
 * Retries failed/pending OCR documents every 30 minutes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class OcrRetryScheduler {

    private final OcrService ocrService;


    @Scheduled(fixedDelay = 1_800_000)
    public void retryPendingOcr() {

        log.debug(
                "Checking for pending OCR documents..."
        );

        try {

            ocrService.processPendingOcr();

        } catch (Exception ex) {

            log.error(
                    "Error while processing pending OCR documents: {}",
                    ex.getMessage(),
                    ex
            );
        }
    }
}