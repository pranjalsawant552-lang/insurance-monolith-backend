package com.insurance.auth.util;

import org.passay.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class PasswordValidator {

    private final org.passay.PasswordValidator validator;

    public PasswordValidator() {
        this.validator = new org.passay.PasswordValidator(Arrays.asList(
                new LengthRule(10, 128),
                new CharacterRule(EnglishCharacterData.UpperCase, 1),
                new CharacterRule(EnglishCharacterData.LowerCase, 1),
                new CharacterRule(EnglishCharacterData.Digit, 1),
                new CharacterRule(EnglishCharacterData.Special, 1),
                new WhitespaceRule(),
                new IllegalSequenceRule(EnglishSequenceData.Alphabetical, 5, false),
                new IllegalSequenceRule(EnglishSequenceData.Numerical, 5, false),
                new IllegalSequenceRule(EnglishSequenceData.USQwerty, 5, false)
        ));
    }

    public List<String> validate(String password) {
        RuleResult result = validator.validate(new PasswordData(password));
        if (result.isValid()) return List.of();
        return validator.getMessages(result);
    }

    public boolean isValid(String password) {
        return validator.validate(new PasswordData(password)).isValid();
    }

    /**
     * Check if new password was used in the last N passwords.
     * passwordHistory is stored as a JSON array of bcrypt hashes.
     */
    public boolean isPasswordReused(String newPassword, String passwordHistoryJson,
                                    PasswordEncoder encoder) {
        if (passwordHistoryJson == null || passwordHistoryJson.equals("[]")) return false;
        // strip brackets and quotes, split on ","
        String stripped = passwordHistoryJson.replaceAll("[\\[\\]\"]", "");
        if (stripped.isBlank()) return false;
        for (String hash : stripped.split(",")) {
            String trimmed = hash.trim();
            if (!trimmed.isEmpty() && encoder.matches(newPassword, trimmed)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Add a new hash to history, keeping only the last maxHistory entries.
     */
    public String addToHistory(String passwordHistoryJson, String newHash, int maxHistory) {
        String stripped = passwordHistoryJson == null ? "" :
                passwordHistoryJson.replaceAll("[\\[\\]\"]", "").trim();

        List<String> hashes = new java.util.ArrayList<>();
        if (!stripped.isBlank()) {
            for (String h : stripped.split(",")) {
                String t = h.trim();
                if (!t.isEmpty()) hashes.add(t);
            }
        }
        hashes.add(0, newHash);
        if (hashes.size() > maxHistory) {
            hashes = hashes.subList(0, maxHistory);
        }
        return "[\"" + String.join("\",\"", hashes) + "\"]";
    }
}
