package com.insurance.analytics.controller;

import com.insurance.analytics.service.AnalyticsService;
import com.insurance.analytics.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Dashboard metrics and report generation")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final ReportService reportService;

    // ── Dashboard ─────────────────────────────────────────────────────────────

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('ANALYTICS_READ')")
    @Operation(summary = "Get full dashboard summary for the current company")
    public ResponseEntity<Map<String, Object>> getDashboard(HttpServletRequest request) {
        return ResponseEntity.ok(
                analyticsService.getDashboardSummary(extractCompanyId(request)));
    }

    @GetMapping("/policies")
    @PreAuthorize("hasAuthority('ANALYTICS_READ')")
    @Operation(summary = "Get policy analytics — by type and status")
    public ResponseEntity<Map<String, Object>> getPolicies(HttpServletRequest request) {
        return ResponseEntity.ok(
                analyticsService.getPolicyAnalytics(extractCompanyId(request)));
    }

    @GetMapping("/claims")
    @PreAuthorize("hasAuthority('ANALYTICS_READ')")
    @Operation(summary = "Get claims analytics — by status and fraud flags")
    public ResponseEntity<Map<String, Object>> getClaims(HttpServletRequest request) {
        return ResponseEntity.ok(
                analyticsService.getClaimsAnalytics(extractCompanyId(request)));
    }

    @GetMapping("/payments")
    @PreAuthorize("hasAuthority('ANALYTICS_READ')")
    @Operation(summary = "Get payment analytics — premium collected, claims disbursed")
    public ResponseEntity<Map<String, Object>> getPayments(HttpServletRequest request) {
        return ResponseEntity.ok(
                analyticsService.getPaymentAnalytics(extractCompanyId(request)));
    }

    // ── Reports ───────────────────────────────────────────────────────────────

    @GetMapping("/reports/dashboard/excel")
    @PreAuthorize("hasAuthority('REPORT_EXPORT')")
    @Operation(summary = "Download dashboard summary as Excel (.xlsx)")
    public ResponseEntity<byte[]> downloadDashboardExcel(HttpServletRequest request) {
        try {
            byte[] data = reportService.generateDashboardExcel(extractCompanyId(request));
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=dashboard-summary.xlsx")
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .contentLength(data.length)
                    .body(data);
        } catch (Exception e) {
            log.error("Excel report generation failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/reports/dashboard/pdf")
    @PreAuthorize("hasAuthority('REPORT_EXPORT')")
    @Operation(summary = "Download dashboard summary as PDF")
    public ResponseEntity<byte[]> downloadDashboardPdf(HttpServletRequest request) {
        try {
            byte[] data = reportService.generateDashboardPdf(extractCompanyId(request));
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=dashboard-summary.pdf")
                    .contentType(MediaType.APPLICATION_PDF)
                    .contentLength(data.length)
                    .body(data);
        } catch (Exception e) {
            log.error("PDF report generation failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/reports/full/excel")
    @PreAuthorize("hasAuthority('REPORT_EXPORT')")
    @Operation(summary = "Download full analytics report as Excel (policies, claims, payments)")
    public ResponseEntity<byte[]> downloadFullExcel(HttpServletRequest request) {
        try {
            byte[] data = reportService.generatePolicyReport(extractCompanyId(request));
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=insurance-analytics-report.xlsx")
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .contentLength(data.length)
                    .body(data);
        } catch (Exception e) {
            log.error("Full report generation failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private String extractCompanyId(HttpServletRequest req) {
        Object id = req.getAttribute("companyId");
        if (id == null) throw new org.springframework.web.server.ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "Missing company context");
        return id.toString();
    }
}
