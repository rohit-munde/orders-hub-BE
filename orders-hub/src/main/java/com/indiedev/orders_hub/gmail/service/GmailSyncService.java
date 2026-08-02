package com.indiedev.orders_hub.gmail.service;

import com.indiedev.orders_hub.gmail.client.GmailApiClient;
import com.indiedev.orders_hub.gmail.config.GmailSearchProperties;
import com.indiedev.orders_hub.gmail.dto.GmailMessageContent;
import com.indiedev.orders_hub.gmail.dto.GmailSyncPreview;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class GmailSyncService {

    private static final Pattern NEWER_THAN = Pattern.compile("[1-9]\\d*[dmy]");
    private static final Pattern SEARCH_VALUE = Pattern.compile("[A-Za-z0-9._@+-]+");

    private final GmailApiClient gmailApiClient;
    private final GmailSearchProperties properties;
    private final GmailOrderParser orderParser;

    public GmailSyncPreview previewFirstOrder(String accessToken) {
        String query = buildQuery();
        String gmailMessageId = gmailApiClient.findFirstMessageId(accessToken, query).orElse(null);
        if (gmailMessageId == null) {
            return new GmailSyncPreview(query, null, null);
        }

        GmailMessageContent message = gmailApiClient.getFullMessage(accessToken, gmailMessageId);
        return new GmailSyncPreview(query, gmailMessageId, orderParser.parse(message));
    }

    private String buildQuery() {
        String newerThan = properties.getNewerThan();
        if (!StringUtils.hasText(newerThan) || !NEWER_THAN.matcher(newerThan).matches()) {
            throw new IllegalArgumentException("Gmail newer-than must look like 30d, 6m, or 1y");
        }

        List<String> rules = new ArrayList<>();
        properties.getSubjectKeywords().forEach(value -> rules.add("subject:" + safeValue(value)));
        properties.getSenderDomains().forEach(value -> rules.add("from:" + safeValue(value)));
        if (rules.isEmpty()) {
            throw new IllegalArgumentException("At least one Gmail search rule is required");
        }

        return "newer_than:" + newerThan + " {" + String.join(" ", rules) + "}";
    }

    private String safeValue(String value) {
        if (!StringUtils.hasText(value) || !SEARCH_VALUE.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid Gmail search value in configuration");
        }
        return value;
    }
}
