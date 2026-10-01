package com.insurance.document.service;

import com.insurance.document.entity.ExtractionTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Applies an {@link ExtractionTemplate}'s field rules against raw extracted
 * text. Each rule's regex must have exactly one capture group; whatever
 * that group matches becomes the field's value.
 *
 * Confidence per field is deliberately simple and explainable rather than a
 * black-box score: 90 if the field matched cleanly, 40 if a required field
 * didn't match at all (still recorded, so a reviewer sees exactly what's
 * missing rather than a blank field with no explanation), 0 for an optional
 * field that didn't match. This is intentionally conservative — the point
 * of the confidence score is to help a human reviewer triage quickly (per
 * the confidence-based routing idea discussed with the client), not to
 * make an autonomous accept/reject decision.
 */
@Slf4j
@Service
public class FieldExtractionService {

    public record ExtractionOutcome(Map<String, Object> fields, Map<String, Integer> confidence, int overallConfidence) {}

    public ExtractionOutcome apply(String rawText, ExtractionTemplate template) {
        Map<String, Object> fields = new LinkedHashMap<>();
        Map<String, Integer> confidence = new LinkedHashMap<>();

        if (rawText == null) rawText = "";

        for (ExtractionTemplate.FieldRule rule : template.getFieldRules()) {
            try {
                Pattern pattern = Pattern.compile(rule.getRegexPattern(), Pattern.CASE_INSENSITIVE);
                Matcher matcher = pattern.matcher(rawText);
                if (matcher.find() && matcher.groupCount() >= 1) {
                    String value = matcher.group(1).trim();
                    fields.put(rule.getFieldName(), value);
                    confidence.put(rule.getFieldName(), 90);
                } else {
                    fields.put(rule.getFieldName(), null);
                    confidence.put(rule.getFieldName(), rule.isRequired() ? 40 : 0);
                    if (rule.isRequired()) {
                        log.debug("Required field '{}' did not match template {} for document type {}",
                                rule.getFieldName(), template.getId(), template.getDocumentType());
                    }
                }
            } catch (PatternSyntaxException e) {
                // A malformed regex in an admin-edited template shouldn't take down the whole
                // extraction — skip just that field and let the reviewer notice it's missing.
                log.error("Invalid regex in template {} for field '{}': {}", template.getId(), rule.getFieldName(), e.getMessage());
                fields.put(rule.getFieldName(), null);
                confidence.put(rule.getFieldName(), 0);
            }
        }

        int overall = confidence.isEmpty() ? 0
                : (int) Math.round(confidence.values().stream().mapToInt(Integer::intValue).average().orElse(0));

        return new ExtractionOutcome(fields, confidence, overall);
    }
}
