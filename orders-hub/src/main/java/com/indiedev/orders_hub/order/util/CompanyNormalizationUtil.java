package com.indiedev.orders_hub.order.util;

import org.springframework.util.StringUtils;

import java.util.Locale;

public final class CompanyNormalizationUtil {

    private CompanyNormalizationUtil() {
    }

    public static String normalizeAliasValue(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return "";
        }

        String normalized = rawValue.strip().toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("^(https?://)?(www\\.)?", "");
        if (normalized.contains("@")) {
            normalized = normalized.substring(normalized.indexOf('@') + 1);
        }
        int slashIndex = normalized.indexOf('/');
        if (slashIndex != -1) {
            normalized = normalized.substring(0, slashIndex);
        }
        return normalized.strip();
    }

    public static String toHumanBrandName(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }

        String value = raw.strip();
        if (value.contains("@")) {
            value = value.substring(value.indexOf('@') + 1);
        }
        value = value.replaceAll("^(https?://)?(www\\.)?", "");
        int slashIndex = value.indexOf('/');
        if (slashIndex != -1) {
            value = value.substring(0, slashIndex);
        }

        // Remove domain extensions if it looks like a domain name
        if (value.matches("(?i).+\\.(com|in|co\\.in|org|store|shop|net|io|app|tech|me|biz|info)$")) {
            value = value.replaceAll("(?i)\\.(com|in|co\\.in|org|store|shop|net|io|app|tech|me|biz|info)$", "");
        }

        String[] words = value.split("[._\\-\\s]+");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(" ");
            }
            sb.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                sb.append(word.substring(1));
            }
        }
        return sb.toString();
    }
}
