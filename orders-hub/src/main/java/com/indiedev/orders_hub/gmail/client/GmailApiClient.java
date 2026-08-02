package com.indiedev.orders_hub.gmail.client;

import com.indiedev.orders_hub.gmail.dto.GmailMessageSummary;
import com.indiedev.orders_hub.gmail.exception.GoogleApiException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Collections;
import java.util.List;

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

    public MessagePage listMessageIds(
            String accessToken,
            String query,
            int maxResults
    ) {
        try {
            MessageListResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/gmail/v1/users/me/messages")
                            .queryParam("maxResults", maxResults)
                            .queryParam("q", "{gmailQuery}")
                            .build(query))
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .body(MessageListResponse.class);

            if (response == null) {
                return new MessagePage(List.of(), false);
            }
            List<String> messageIds = response.messages() == null
                    ? List.of()
                    : response.messages().stream().map(MessageReference::id).toList();
            return new MessagePage(messageIds, StringUtils.hasText(response.nextPageToken()));
        } catch (RestClientException exception) {
            throw new GoogleApiException("Unable to search Gmail messages", exception);
        }
    }

    public GmailMessageSummary getMessageMetadata(String accessToken, String gmailMessageId) {
        try {
            MessageResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/gmail/v1/users/me/messages/{messageId}")
                            .queryParam("format", "metadata")
                            .queryParam("metadataHeaders", "Subject", "From", "Date", "Message-ID")
                            .build(gmailMessageId))
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .body(MessageResponse.class);

            if (response == null) {
                throw new GoogleApiException("Gmail message metadata response was empty");
            }

            List<MessageHeader> headers = response.payload() == null || response.payload().headers() == null
                    ? Collections.emptyList()
                    : response.payload().headers();

            return new GmailMessageSummary(
                    response.id(),
                    response.threadId(),
                    headerValue(headers, "Subject"),
                    headerValue(headers, "From"),
                    headerValue(headers, "Date"),
                    headerValue(headers, "Message-ID")
            );
        } catch (GoogleApiException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new GoogleApiException("Unable to fetch Gmail message metadata", exception);
        }
    }

    private String headerValue(List<MessageHeader> headers, String name) {
        return headers.stream()
                .filter(header -> name.equalsIgnoreCase(header.name()))
                .map(MessageHeader::value)
                .findFirst()
                .orElse(null);
    }

    public record MessagePage(List<String> messageIds, boolean hasNextPage) {
        public MessagePage {
            messageIds = List.copyOf(messageIds);
        }
    }

    private record ProfileResponse(String emailAddress) {
    }

    private record MessageListResponse(List<MessageReference> messages, String nextPageToken) {
    }

    private record MessageReference(String id) {
    }

    private record MessageResponse(String id, String threadId, MessagePayload payload) {
    }

    private record MessagePayload(List<MessageHeader> headers) {
    }

    private record MessageHeader(String name, String value) {
    }
}
