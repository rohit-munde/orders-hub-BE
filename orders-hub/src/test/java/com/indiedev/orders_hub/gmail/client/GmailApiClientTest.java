package com.indiedev.orders_hub.gmail.client;

import com.indiedev.orders_hub.gmail.dto.GmailMessageSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GmailApiClientTest {

    private MockRestServiceServer server;
    private GmailApiClient gmailApiClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        gmailApiClient = new GmailApiClient(builder);
    }

    @Test
    void fetchesProfileSearchesMessagesAndMapsMetadataHeaders() {
        server.expect(once(), request -> {
                    assertEquals("/gmail/v1/users/me/profile", request.getURI().getPath());
                    assertEquals("Bearer access-token", request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
                })
                .andRespond(withSuccess("""
                        {"emailAddress":"shopper@gmail.com"}
                        """, MediaType.APPLICATION_JSON));

        server.expect(once(), request -> {
                    assertEquals("/gmail/v1/users/me/messages", request.getURI().getPath());
                    String query = URLDecoder.decode(request.getURI().getRawQuery(), StandardCharsets.UTF_8);
                    assertTrue(query.contains("maxResults=10"));
                    assertTrue(query.contains("q=" + GmailApiClient.ORDER_SEARCH_QUERY));
                })
                .andRespond(withSuccess("""
                        {
                          "messages": [
                            {"id":"message-1","threadId":"thread-1"},
                            {"id":"message-2","threadId":"thread-2"}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        expectMetadata("message-1", "thread-1", "Your order shipped", "Store <orders@store.com>", "Fri, 1 Aug 2026 10:00:00 +0000");
        expectMetadata("message-2", "thread-2", "Invoice available", "Billing <billing@store.com>", "Thu, 31 Jul 2026 09:00:00 +0000");

        GmailProfile profile = gmailApiClient.getProfile("access-token");
        List<GmailMessageSummary> messages = gmailApiClient.searchOrderEmails("access-token");

        assertEquals("shopper@gmail.com", profile.emailAddress());
        assertEquals(2, messages.size());
        assertEquals("Your order shipped", messages.getFirst().subject());
        assertEquals("Billing <billing@store.com>", messages.get(1).from());
        server.verify();
    }

    private void expectMetadata(
            String messageId,
            String threadId,
            String subject,
            String from,
            String date
    ) {
        server.expect(once(), request -> {
                    assertEquals("/gmail/v1/users/me/messages/" + messageId, request.getURI().getPath());
                    String query = URLDecoder.decode(request.getURI().getRawQuery(), StandardCharsets.UTF_8);
                    assertTrue(query.contains("format=metadata"));
                    assertTrue(query.contains("metadataHeaders=Subject"));
                    assertTrue(query.contains("metadataHeaders=From"));
                    assertTrue(query.contains("metadataHeaders=Date"));
                })
                .andRespond(withSuccess("""
                        {
                          "id": "%s",
                          "threadId": "%s",
                          "payload": {
                            "headers": [
                              {"name":"Subject","value":"%s"},
                              {"name":"From","value":"%s"},
                              {"name":"Date","value":"%s"}
                            ]
                          }
                        }
                        """.formatted(messageId, threadId, subject, from, date), MediaType.APPLICATION_JSON));
    }
}
