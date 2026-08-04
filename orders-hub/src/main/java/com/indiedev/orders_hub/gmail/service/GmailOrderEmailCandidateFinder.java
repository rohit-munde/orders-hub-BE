package com.indiedev.orders_hub.gmail.service;

import com.indiedev.orders_hub.gmail.client.GmailApiClient;
import com.indiedev.orders_hub.gmail.config.GmailSearchProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class GmailOrderEmailCandidateFinder implements OrderEmailCandidateFinder {

    private static final Pattern SEARCH_VALUE = Pattern.compile("[A-Za-z0-9._@+-]+");
    private static final int MAX_LOOKBACK_DAYS = 45;
    private static final int MAX_BATCH_SIZE = 50;

    private final GmailApiClient gmailApiClient;
    private final GmailSearchProperties properties;

    @Override
    public CandidateBatch find(String accessToken) {
        String query = buildQuery();
        int batchSize = Math.max(1, Math.min(properties.getBatchSize(), MAX_BATCH_SIZE));
        return new CandidateBatch(
                query,
                gmailApiClient.findMessageIds(accessToken, query, batchSize)
        );
    }

    private String buildQuery() {
        int lookbackDays = properties.getLookbackDays();
        if (lookbackDays < 1 || lookbackDays > MAX_LOOKBACK_DAYS) {
            throw new IllegalArgumentException("Gmail lookback days must be between 1 and 45");
        }

        List<String> rules = new ArrayList<>();
        properties.getSubjectKeywords().forEach(value -> rules.add("subject:" + safeValue(value)));
        properties.getSenderDomains().forEach(value -> rules.add("from:" + safeValue(value)));
        if (rules.isEmpty()) {
            throw new IllegalArgumentException("At least one Gmail search rule is required");
        }
        return "newer_than:" + lookbackDays + "d {" + String.join(" ", rules) + "}";
    }

    private String safeValue(String value) {
        if (!StringUtils.hasText(value) || !SEARCH_VALUE.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid Gmail search value in configuration");
        }
        return value;
    }
}
