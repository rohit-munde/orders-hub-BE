package com.indiedev.orders_hub.gmail.service;

import com.indiedev.orders_hub.gmail.client.GmailApiClient;
import com.indiedev.orders_hub.gmail.config.GmailSearchProperties;
import com.indiedev.orders_hub.gmail.dto.GmailMessageSummary;
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
    private static final int MAX_PREVIEW_MESSAGES = 25;

    private final GmailApiClient gmailApiClient;
    private final GmailSearchProperties properties;

    public GmailSyncPreview previewFirstPage(String accessToken) {
        String query = buildQuery();
        int messageLimit = Math.max(1, Math.min(properties.getMaxResults(), MAX_PREVIEW_MESSAGES));
        GmailApiClient.MessagePage page = gmailApiClient.listMessageIds(accessToken, query, messageLimit);
        List<GmailMessageSummary> messages = page.messageIds().stream()
                .limit(messageLimit)
                .map(messageId -> gmailApiClient.getMessageMetadata(accessToken, messageId))
                .toList();

        return new GmailSyncPreview(query, messages.size(), page.hasNextPage(), messages);
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
