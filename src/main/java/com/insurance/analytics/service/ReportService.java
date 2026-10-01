package com.insurance.analytics.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final AnalyticsService analyticsService;

    @Value("${report.storage.path:/tmp/reports}")
    private String reportStoragePath;

    // ── Dashboard summary Excel report ────────────────────────────────────────

    public byte[] generateDashboardExcel(String companyId) throws IOException {
        Map<String, Object> data = analyticsService.getDashboardSummary(companyId);

        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            XSSFSheet sheet = wb.createSheet("Dashboard Summary");
            sheet.setColumnWidth(0, 9000);
            sheet.setColumnWidth(1, 6000);

            // Styles
            XSSFCellStyle headerStyle = createHeaderStyle(wb);
            XSSFCellStyle titleStyle  = createTitleStyle(wb);
            XSSFCellStyle dataStyle   = createDataStyle(wb);
            XSSFCellStyle numStyle    = createNumberStyle(wb);

            int row = 0;

            // Title
            Row titleRow = sheet.createRow(row++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("Insurance Platform — Dashboard Summary");
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 1));

            // Date
            Row dateRow = sheet.createRow(row++);
            dateRow.createCell(0).setCellValue("Generated: " +
                    ZonedDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm")));
            row++; // blank row

            // Section headers
            addSectionHeader(sheet, row++, "Metric", "Value", headerStyle);

            // Data rows
            String[][] rows = {
                {"Total Policies",          str(data, "totalPolicies")},
                {"Active Policies",         str(data, "activePolicies")},
                {"Lapsed Policies",         str(data, "lapsedPolicies")},
                {"Total Customers",         str(data, "totalCustomers")},
                {"KYC Verified Customers",  str(data, "kycVerifiedCustomers")},
                {"Total Claims",            str(data, "totalClaims")},
                {"Open Claims",             str(data, "openClaims")},
                {"Settled Claims",          str(data, "settledClaims")},
                {"Total Agents",            str(data, "totalAgents")},
                {"Premium Collected (₹)",   str(data, "totalPremiumCollected")},
                {"Claims Settled (₹)",      str(data, "totalClaimsSettled")},
            };

            for (String[] r : rows) {
                Row dataRow = sheet.createRow(row++);
                Cell label = dataRow.createCell(0);
                Cell value = dataRow.createCell(1);
                label.setCellValue(r[0]);
                label.setCellStyle(dataStyle);
                try {
                    value.setCellValue(Double.parseDouble(r[1]));
                    value.setCellStyle(numStyle);
                } catch (NumberFormatException e) {
                    value.setCellValue(r[1]);
                    value.setCellStyle(dataStyle);
                }
            }

            wb.write(out);
            return out.toByteArray();
        }
    }

    // ── Policy analytics Excel report ─────────────────────────────────────────

    public byte[] generatePolicyReport(String companyId) throws IOException {
        Map<String, Object> policyData   = analyticsService.getPolicyAnalytics(companyId);
        Map<String, Object> claimsData   = analyticsService.getClaimsAnalytics(companyId);
        Map<String, Object> paymentData  = analyticsService.getPaymentAnalytics(companyId);

        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            buildPolicySheet(wb, policyData);
            buildClaimsSheet(wb, claimsData);
            buildPaymentSheet(wb, paymentData);

            wb.write(out);
            return out.toByteArray();
        }
    }

    // ── PDF summary report (HTML → byte[]) ───────────────────────────────────

    public byte[] generateDashboardPdf(String companyId) {
        Map<String, Object> data = analyticsService.getDashboardSummary(companyId);
        String html = buildDashboardHtml(data, companyId);
        // In production: use Flying Saucer / iText / JasperReports to convert HTML → PDF
        // For now we return the HTML bytes with a PDF content-type hint
        // Replace this with: ITextRenderer renderer = new ITextRenderer(); renderer.setDocumentFromString(html);
        log.info("PDF generation placeholder for company {}", companyId);
        return html.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    // ── Private: sheet builders ───────────────────────────────────────────────

    private void buildPolicySheet(XSSFWorkbook wb, Map<String, Object> data) {
        XSSFSheet sheet = wb.createSheet("Policies");
        sheet.setColumnWidth(0, 8000);
        sheet.setColumnWidth(1, 5000);
        XSSFCellStyle header = createHeaderStyle(wb);
        XSSFCellStyle cell   = createDataStyle(wb);

        int row = 0;
        addSectionHeader(sheet, row++, "Insurance Type", "Count", header);

        @SuppressWarnings("unchecked")
        Map<String, Object> byType = (Map<String, Object>) data.getOrDefault("byType", Map.of());
        for (Map.Entry<String, Object> e : byType.entrySet()) {
            Row r = sheet.createRow(row++);
            r.createCell(0).setCellValue(e.getKey());
            r.createCell(1).setCellValue(toDouble(e.getValue()));
            r.getCell(0).setCellStyle(cell);
        }
        row++;
        addSectionHeader(sheet, row++, "Status", "Count", header);

        @SuppressWarnings("unchecked")
        Map<String, Object> byStatus = (Map<String, Object>) data.getOrDefault("byStatus", Map.of());
        for (Map.Entry<String, Object> e : byStatus.entrySet()) {
            Row r = sheet.createRow(row++);
            r.createCell(0).setCellValue(e.getKey());
            r.createCell(1).setCellValue(toDouble(e.getValue()));
            r.getCell(0).setCellStyle(cell);
        }
    }

    private void buildClaimsSheet(XSSFWorkbook wb, Map<String, Object> data) {
        XSSFSheet sheet = wb.createSheet("Claims");
        sheet.setColumnWidth(0, 8000);
        sheet.setColumnWidth(1, 5000);
        XSSFCellStyle header = createHeaderStyle(wb);

        int row = 0;
        addSectionHeader(sheet, row++, "Status", "Count", header);

        @SuppressWarnings("unchecked")
        Map<String, Object> byStatus = (Map<String, Object>) data.getOrDefault("byStatus", Map.of());
        for (Map.Entry<String, Object> e : byStatus.entrySet()) {
            Row r = sheet.createRow(row++);
            r.createCell(0).setCellValue(e.getKey());
            r.createCell(1).setCellValue(toDouble(e.getValue()));
        }
    }

    private void buildPaymentSheet(XSSFWorkbook wb, Map<String, Object> data) {
        XSSFSheet sheet = wb.createSheet("Payments");
        sheet.setColumnWidth(0, 9000);
        sheet.setColumnWidth(1, 6000);
        XSSFCellStyle header = createHeaderStyle(wb);

        int row = 0;
        addSectionHeader(sheet, row++, "Metric", "Value (₹)", header);
        String[][] rows = {
            {"Total Premium Collected", str(data, "totalPremiumCollected")},
            {"Total Claims Disbursed",  str(data, "totalClaimsDisbursed")},
            {"Total Transactions",      str(data, "totalTransactions")},
            {"Failed Payments",         str(data, "failedPayments")},
            {"Overdue Count",           str(data, "overdueCount")},
        };
        for (String[] r : rows) {
            Row dataRow = sheet.createRow(row++);
            dataRow.createCell(0).setCellValue(r[0]);
            try { dataRow.createCell(1).setCellValue(Double.parseDouble(r[1])); }
            catch (Exception e) { dataRow.createCell(1).setCellValue(r[1]); }
        }
    }

    // ── HTML report template for PDF conversion ───────────────────────────────

    private String buildDashboardHtml(Map<String, Object> data, String companyId) {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy"));
        return "<!DOCTYPE html><html><head><style>"
                + "body{font-family:Arial,sans-serif;padding:32px;color:#1e293b;}"
                + "h1{font-size:22px;color:#1e40af;border-bottom:2px solid #1e40af;padding-bottom:8px;}"
                + "h2{font-size:16px;color:#334155;margin-top:24px;}"
                + "table{width:100%;border-collapse:collapse;margin:12px 0;}"
                + "th{background:#1e40af;color:#fff;padding:10px 12px;text-align:left;font-size:13px;}"
                + "td{padding:10px 12px;border-bottom:1px solid #e2e8f0;font-size:13px;}"
                + "tr:nth-child(even) td{background:#f8fafc;}"
                + ".num{text-align:right;font-weight:500;}"
                + "</style></head><body>"
                + "<h1>Insurance Platform — Dashboard Report</h1>"
                + "<p>Company ID: " + companyId + " &nbsp;|&nbsp; Generated: " + date + "</p>"
                + "<h2>Key Metrics</h2>"
                + "<table><tr><th>Metric</th><th class='num'>Value</th></tr>"
                + tr("Total Policies",         str(data, "totalPolicies"))
                + tr("Active Policies",        str(data, "activePolicies"))
                + tr("Lapsed Policies",        str(data, "lapsedPolicies"))
                + tr("Total Customers",        str(data, "totalCustomers"))
                + tr("KYC Verified Customers", str(data, "kycVerifiedCustomers"))
                + tr("Total Claims",           str(data, "totalClaims"))
                + tr("Open Claims",            str(data, "openClaims"))
                + tr("Settled Claims",         str(data, "settledClaims"))
                + tr("Total Agents",           str(data, "totalAgents"))
                + tr("Premium Collected (₹)",  str(data, "totalPremiumCollected"))
                + tr("Claims Settled (₹)",     str(data, "totalClaimsSettled"))
                + "</table></body></html>";
    }

    private String tr(String label, String value) {
        return "<tr><td>" + label + "</td><td class='num'>" + value + "</td></tr>";
    }

    // ── Cell style helpers ────────────────────────────────────────────────────

    private XSSFCellStyle createHeaderStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(new XSSFColor(new byte[]{(byte)30, (byte)64, (byte)175}, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }

    private XSSFCellStyle createTitleStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 14);
        style.setFont(font);
        return style;
    }

    private XSSFCellStyle createDataStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        return style;
    }

    private XSSFCellStyle createNumberStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = createDataStyle(wb);
        XSSFDataFormat format = wb.createDataFormat();
        style.setDataFormat(format.getFormat("#,##0.00"));
        style.setAlignment(HorizontalAlignment.RIGHT);
        return style;
    }

    private void addSectionHeader(XSSFSheet sheet, int rowNum,
                                   String col1, String col2, XSSFCellStyle style) {
        Row row = sheet.createRow(rowNum);
        Cell c1 = row.createCell(0);
        Cell c2 = row.createCell(1);
        c1.setCellValue(col1);
        c2.setCellValue(col2);
        c1.setCellStyle(style);
        c2.setCellStyle(style);
    }

    private String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v != null ? v.toString() : "0";
    }

    private double toDouble(Object v) {
        if (v == null) return 0;
        try { return Double.parseDouble(v.toString().split("\\.")[0]); }
        catch (Exception e) { return 0; }
    }
}
