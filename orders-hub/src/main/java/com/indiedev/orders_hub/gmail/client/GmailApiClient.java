package com.indiedev.orders_hub.gmail.client;

import com.indiedev.orders_hub.gmail.dto.GmailMessageSummary;
import com.indiedev.orders_hub.gmail.exception.GmailApiException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Collections;
import java.util.List;

@Component
public class GmailApiClient {

    static final String GMAIL_BASE_URL = "https://gmail.googleapis.com";
    static final String ORDER_SEARCH_QUERY =
            "subject:(order OR ordered OR shipped OR delivered OR dispatched OR invoice) newer_than:1y";

    private final RestClient restClient;

    public GmailApiClient(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.baseUrl(GMAIL_BASE_URL).build();
    }

    public GmailProfile getProfile(String accessToken) {
        try {
            GmailProfile profile = restClient.get()
                    .uri("/gmail/v1/users/me/profile")
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .body(GmailProfile.class);
            if (profile == null || profile.emailAddress() == null || profile.emailAddress().isBlank()) {
                throw new GmailApiException("Gmail profile response did not include an email address", null);
            }
            return profile;
        } catch (GmailApiException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new GmailApiException("Unable to fetch Gmail profile", exception);
        }
    }

    public List<GmailMessageSummary> searchOrderEmails(String accessToken) {
        try {
            MessageListResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/gmail/v1/users/me/messages")
                            .queryParam("maxResults", 10)
                            .queryParam("q", ORDER_SEARCH_QUERY)
                            .build())
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .body(MessageListResponse.class);

            List<MessageReference> references = response == null || response.messages() == null
                    ? Collections.emptyList()
                    : response.messages();

            return references.stream()
                    .map(reference -> getMessageMetadata(accessToken, reference.id()))
                    .toList();
        } catch (GmailApiException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new GmailApiException("Unable to search Gmail messages", exception);
        }
    }

    private GmailMessageSummary getMessageMetadata(String accessToken, String messageId) {
        try {
            MessageResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/gmail/v1/users/me/messages/{messageId}")
                            .queryParam("format", "metadata")
                            .queryParam("metadataHeaders", "Subject", "From", "Date")
                            .build(messageId))
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .body(MessageResponse.class);

            if (response == null) {
                throw new GmailApiException("Gmail message metadata response was empty", null);
            }

            List<MessageHeader> headers = response.payload() == null || response.payload().headers() == null
                    ? Collections.emptyList()
                    : response.payload().headers();

            return new GmailMessageSummary(
                    response.id(),
                    response.threadId(),
                    headerValue(headers, "Subject"),
                    headerValue(headers, "From"),
                    headerValue(headers, "Date")
            );
        } catch (GmailApiException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new GmailApiException("Unable to fetch Gmail message metadata", exception);
        }
    }

    private String headerValue(List<MessageHeader> headers, String name) {
        return headers.stream()
                .filter(header -> name.equalsIgnoreCase(header.name()))
                .map(MessageHeader::value)
                .findFirst()
                .orElse(null);
    }

    record MessageListResponse(List<MessageReference> messages) {
    }

    record MessageReference(String id, String threadId) {
    }

    record MessageResponse(String id, String threadId, MessagePayload payload) {
    }

    record MessagePayload(List<MessageHeader> headers) {
    }

    record MessageHeader(String name, String value) {
    }
}
