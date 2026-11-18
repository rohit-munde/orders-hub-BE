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

    private static final java.util.regex.Pattern LEGAL_SUFFIX = java.util.regex.Pattern.compile(
            "(?i)[\\s,]+(private\\s+limited|pvt\\.?\\s*ltd\\.?|pvt\\.?|limited|ltd\\.?|llp|inc\\.?|corp\\.?|corporation)\\s*$"
    );

    /**
     * The merchant name shown to users: drops legal suffixes such as "Private Limited" or
     * "Pvt. Ltd." so "Cases Villa Private Limited" reads as "Cases Villa". Returns the input
     * unchanged if nothing would be left.
     */
    public static String toDisplayBrandName(String brandName) {
        if (!StringUtils.hasText(brandName)) {
            return brandName;
        }
        String value = brandName.strip();
        String previous;
        do {
            previous = value;
            value = LEGAL_SUFFIX.matcher(value).replaceAll("").strip();
        } while (!value.equals(previous) && StringUtils.hasText(value));
        return StringUtils.hasText(value) ? value : brandName.strip();
    }
}
