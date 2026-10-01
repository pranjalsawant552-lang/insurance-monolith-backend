package com.insurance.policy.client;

import com.insurance.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * MONOLITH CONVERSION NOTE: originally an HTTP call to product-service's
 * premium-preview endpoint. Now a direct, in-process call to ProductService
 * — product-service's premium engine remains the single source of truth
 * for pricing (policy-service still doesn't maintain its own copy), it's
 * just invoked as a Java method call instead of a REST round-trip.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductServiceClient {

    private final ProductService productService;

    public record PremiumQuote(
            UUID calculationId,
            String productCode,
            String productName,
            String insuranceType,
            int productVersion,
            BigDecimal basePremium,
            List<Map<String, Object>> factorsApplied,
            BigDecimal riderPremium,
            BigDecimal gstAmount,
            BigDecimal totalPremium
    ) {}

    @SuppressWarnings("unchecked")
    public PremiumQuote calculatePremium(UUID productId, BigDecimal sumAssured, String frequency, int termYears,
                                          List<UUID> riderIds, Integer age, BigDecimal riskScore,
                                          String location, String vehicleType, UUID requestedBy) {
        Map<String, Object> data;
        try {
            data = productService.calculatePremiumPreview(productId, sumAssured, frequency, termYears,
                    riderIds, age, riskScore, location, vehicleType, requestedBy);
        } catch (RuntimeException e) {
            log.error("Premium calculation failed for product {}: {}", productId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Premium calculation failed: " + e.getMessage());
        }
        if (data == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Pricing engine returned no result.");
        }
        return new PremiumQuote(
                UUID.fromString(data.get("calculationId").toString()),
                String.valueOf(data.get("productCode")),
                String.valueOf(data.get("productName")),
                String.valueOf(data.get("insuranceType")),
                ((Number) data.get("productVersion")).intValue(),
                new BigDecimal(data.get("basePremium").toString()),
                (List<Map<String, Object>>) data.get("factorsApplied"),
                new BigDecimal(data.get("riderPremium").toString()),
                new BigDecimal(data.get("gstAmount").toString()),
                new BigDecimal(data.get("totalPremium").toString())
        );
    }
}
