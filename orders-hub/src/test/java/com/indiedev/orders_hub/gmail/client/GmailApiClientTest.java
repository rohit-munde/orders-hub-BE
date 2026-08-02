package com.indiedev.orders_hub.gmail.client;

import com.indiedev.orders_hub.gmail.dto.GmailMessageSummary;
import com.indiedev.orders_hub.gmail.exception.GoogleApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GmailApiClientTest {

    private MockRestServiceServer server;
    private GmailApiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GmailApiClient(builder);
    }

    @Test
    void fetchesProfileListsOnePageAndMapsSafeMetadata() {
        server.expect(once(), request -> {
                    assertEquals("/gmail/v1/users/me/profile", request.getURI().getPath());
                    assertEquals("Bearer access-token", request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
                })
                .andRespond(withSuccess("{\"emailAddress\":\"shopper@gmail.com\"}", MediaType.APPLICATION_JSON));

        server.expect(once(), request -> {
                    String query = URLDecoder.decode(request.getURI().getRawQuery(), StandardCharsets.UTF_8);
                    assertEquals("/gmail/v1/users/me/messages", request.getURI().getPath());
                    assertTrue(query.contains("maxResults=25"));
                    assertTrue(query.contains("q=newer_than:1y {subject:order}"));
                    assertFalse(query.contains("pageToken"));
                })
                .andRespond(withSuccess("""
                        {"messages":[{"id":"message-1","threadId":"thread-1"}],"nextPageToken":"next-2"}
                        """, MediaType.APPLICATION_JSON));

        expectMetadata();

        assertEquals("shopper@gmail.com", client.getProfileEmail("access-token"));
        GmailApiClient.MessagePage page = client.listMessageIds(
                "access-token", "newer_than:1y {subject:order}", 25
        );
        GmailMessageSummary message = client.getMessageMetadata("access-token", page.messageIds().getFirst());

        assertTrue(page.hasNextPage());
        assertEquals("Your order shipped", message.subject());
        assertEquals("<rfc-message-id@store.com>", message.messageId());
        server.verify();
    }

    @Test
    void convertsGoogleFailureToSafeGmailApiException() {
        server.expect(once(), request -> assertEquals("/gmail/v1/users/me/profile", request.getURI().getPath()))
                .andRespond(withServerError());

        GoogleApiException exception = assertThrows(
                GoogleApiException.class,
                () -> client.getProfileEmail("secret-access-token")
        );
        assertEquals("Unable to fetch Gmail profile", exception.getMessage());
        assertFalse(exception.getMessage().contains("secret-access-token"));
    }

    private void expectMetadata() {
        server.expect(once(), request -> {
                    assertEquals("/gmail/v1/users/me/messages/message-1", request.getURI().getPath());
                    String query = URLDecoder.decode(request.getURI().getRawQuery(), StandardCharsets.UTF_8);
                    assertTrue(query.contains("format=metadata"));
                    assertTrue(query.contains("metadataHeaders=Message-ID"));
                })
                .andRespond(withSuccess("""
                        {
                          "id":"message-1","threadId":"thread-1",
                          "payload":{"headers":[
                            {"name":"Subject","value":"Your order shipped"},
                            {"name":"From","value":"Store <orders@store.com>"},
                            {"name":"Date","value":"Fri, 1 Aug 2026 10:00:00 +0000"},
                            {"name":"Message-ID","value":"<rfc-message-id@store.com>"}
                          ]}
                        }
                        """, MediaType.APPLICATION_JSON));
    }
}
