package com.indiedev.orders_hub.gmail.client;

import com.indiedev.orders_hub.gmail.dto.GmailMessageContent;
import com.indiedev.orders_hub.exception.GoogleApiException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class GmailApiClient {

    static final String GMAIL_BASE_URL = "https://gmail.googleapis.com";

    private final RestClient restClient;

    public GmailApiClient(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.baseUrl(GMAIL_BASE_URL).build();
    }

    public String getProfileEmail(String accessToken) {
        try {
            ProfileResponse profile = restClient.get()
                    .uri("/gmail/v1/users/me/profile")
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .body(ProfileResponse.class);
            if (profile == null || !StringUtils.hasText(profile.emailAddress())) {
                throw new GoogleApiException("Gmail profile response did not include an email address");
            }
            return profile.emailAddress();
        } catch (GoogleApiException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new GoogleApiException("Unable to fetch Gmail profile", exception);
        }
    }

    public List<String> findMessageIds(String accessToken, String query, int maxResults) {
        try {
            LinkedHashSet<String> ids = new LinkedHashSet<>();
            Set<String> seenPageTokens = new HashSet<>();
            String pageToken = null;

            while (ids.size() < maxResults) {
                int remaining = maxResults - ids.size();
                MessageListResponse response = findMessagePage(
                        accessToken,
                        query,
                        remaining,
                        pageToken
                );
                if (response == null || response.messages() == null || response.messages().isEmpty()) {
                    break;
                }
                for (MessageReference message : response.messages()) {
                    if (message != null && StringUtils.hasText(message.id())) {
                        ids.add(message.id());
                        if (ids.size() == maxResults) {
                            break;
                        }
                    }
                }
                String nextPageToken = response.nextPageToken();
                if (!StringUtils.hasText(nextPageToken) || !seenPageTokens.add(nextPageToken)) {
                    break;
                }
                pageToken = nextPageToken;
            }
            return List.copyOf(ids);
        } catch (RestClientException exception) {
            throw new GoogleApiException("Unable to search Gmail messages", exception);
        }
    }

    private MessageListResponse findMessagePage(
            String accessToken,
            String query,
            int maxResults,
            String pageToken
    ) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/gmail/v1/users/me/messages")
                        .queryParam("maxResults", maxResults)
                        .queryParam("q", "{gmailQuery}")
                        .queryParamIfPresent(
                                "pageToken",
                                StringUtils.hasText(pageToken)
                                        ? Optional.of(pageToken)
                                        : Optional.empty()
                        )
                        .build(query))
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(MessageListResponse.class);
    }

    public GmailMessageContent getFullMessage(String accessToken, String gmailMessageId) {
        try {
            MessageResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/gmail/v1/users/me/messages/{messageId}")
                            .queryParam("format", "full")
                            .build(gmailMessageId))
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .body(MessageResponse.class);

            if (response == null) {
                throw new GoogleApiException("Gmail message response was empty");
            }

            List<MessageHeader> headers = response.payload() == null || response.payload().headers() == null
                    ? Collections.emptyList()
                    : response.payload().headers();

            return new GmailMessageContent(
                    response.id(),
                    headerValue(headers, "Subject"),
                    headerValue(headers, "From"),
                    readableBody(response.payload()),
                    receivedAt(response.internalDate())
            );
        } catch (GoogleApiException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new GoogleApiException("Unable to fetch Gmail message", exception);
        }
    }

    private String headerValue(List<MessageHeader> headers, String name) {
        return headers.stream()
                .filter(header -> name.equalsIgnoreCase(header.name()))
                .map(MessageHeader::value)
                .findFirst()
                .orElse(null);
    }

    private Instant receivedAt(String internalDate) {
        if (!StringUtils.hasText(internalDate)) {
            return null;
        }
        try {
            return Instant.ofEpochMilli(Long.parseLong(internalDate));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String readableBody(MessagePayload payload) {
        String plainText = findBody(payload, "text/plain");
        if (StringUtils.hasText(plainText)) {
            return plainText.strip();
        }

        String html = findBody(payload, "text/html");
        return StringUtils.hasText(html) ? htmlToText(html) : "";
    }

    private String findBody(MessagePayload payload, String mimeType) {
        if (payload == null || StringUtils.hasText(payload.filename())) {
            return null;
        }
        if (mimeType.equalsIgnoreCase(payload.mimeType()) && payload.body() != null) {
            String decoded = decode(payload.body().data());
            if (StringUtils.hasText(decoded)) {
                return decoded;
            }
        }
        if (payload.parts() != null) {
            for (MessagePayload part : payload.parts()) {
                String decoded = findBody(part, mimeType);
                if (StringUtils.hasText(decoded)) {
                    return decoded;
                }
            }
        }
        return null;
    }

    private String decode(String encodedBody) {
        if (!StringUtils.hasText(encodedBody)) {
            return null;
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(encodedBody);
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String htmlToText(String html) {
        String text = html
                .replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</(?:p|div|li|tr|h[1-6])>", "\n")
                .replaceAll("(?s)<[^>]+>", " ");
        return HtmlUtils.htmlUnescape(text)
                .replace('\u00a0', ' ')
                .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll(" *\n *", "\n")
                .replaceAll("\n{3,}", "\n\n")
                .strip();
    }

    private record ProfileResponse(String emailAddress) {
    }

    private record MessageListResponse(List<MessageReference> messages, String nextPageToken) {
    }

    private record MessageReference(String id) {
    }

    private record MessageResponse(String id, String internalDate, MessagePayload payload) {
    }

    private record MessagePayload(
            String mimeType,
            String filename,
            MessageBody body,
            List<MessagePayload> parts,
            List<MessageHeader> headers
    ) {
    }

    private record MessageBody(String data) {
    }

    private record MessageHeader(String name, String value) {
    }
}
