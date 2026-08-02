package com.indiedev.orders_hub.gmail.client;

import com.indiedev.orders_hub.gmail.dto.GmailMessageContent;
import com.indiedev.orders_hub.gmail.exception.GoogleApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

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
    void fetchesGmailProfile() {
        server.expect(once(), request -> {
                    assertEquals("/gmail/v1/users/me/profile", request.getURI().getPath());
                    assertEquals("Bearer access-token", request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
                })
                .andRespond(withSuccess("{\"emailAddress\":\"shopper@gmail.com\"}", MediaType.APPLICATION_JSON));

        assertEquals("shopper@gmail.com", client.getProfileEmail("access-token"));
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

    @Test
    void findsOnlyTheFirstMatchingMessageId() {
        server.expect(once(), request -> {
                    String query = URLDecoder.decode(request.getURI().getRawQuery(), StandardCharsets.UTF_8);
                    assertEquals("/gmail/v1/users/me/messages", request.getURI().getPath());
                    assertTrue(query.contains("maxResults=1"));
                    assertTrue(query.contains("q=subject:order"));
                })
                .andRespond(withSuccess("""
                        {"messages":[{"id":"message-1","threadId":"thread-1"}]}
                        """, MediaType.APPLICATION_JSON));

        assertEquals(
                "message-1",
                client.findFirstMessageId("access-token", "subject:order").orElseThrow()
        );
        server.verify();
    }

    @Test
    void fetchesFullMessageAndPrefersNestedPlainTextBody() {
        String plainBody = "Order number: ORDER-123";
        String htmlBody = "<p>Wrong HTML fallback</p>";
        server.expect(once(), request -> {
                    assertEquals("/gmail/v1/users/me/messages/message-1", request.getURI().getPath());
                    String query = URLDecoder.decode(request.getURI().getRawQuery(), StandardCharsets.UTF_8);
                    assertTrue(query.contains("format=full"));
                })
                .andRespond(withSuccess("""
                        {
                          "id":"message-1",
                          "payload":{
                            "mimeType":"multipart/alternative",
                            "headers":[
                              {"name":"Subject","value":"Your order shipped"},
                              {"name":"From","value":"Amazon <orders@amazon.in>"}
                            ],
                            "parts":[
                              {"mimeType":"text/html","body":{"data":"%s"}},
                              {"mimeType":"multipart/mixed","parts":[
                                {"mimeType":"text/plain","body":{"data":"%s"}}
                              ]}
                            ]
                          }
                        }
                        """.formatted(base64Url(htmlBody), base64Url(plainBody)), MediaType.APPLICATION_JSON));

        GmailMessageContent message = client.getFullMessage("access-token", "message-1");

        assertEquals("message-1", message.gmailMessageId());
        assertEquals("Your order shipped", message.subject());
        assertEquals("Amazon <orders@amazon.in>", message.from());
        assertEquals(plainBody, message.body());
        server.verify();
    }

    @Test
    void convertsHtmlBodyToReadableTextWhenPlainTextIsMissing() {
        String htmlBody = "<html><body><p>Order number: ORDER-456</p><p>Total: INR 299.00</p></body></html>";
        server.expect(once(), request -> {
                    assertEquals("/gmail/v1/users/me/messages/message-2", request.getURI().getPath());
                    assertTrue(request.getURI().getQuery().contains("format=full"));
                })
                .andRespond(withSuccess("""
                        {
                          "id":"message-2",
                          "payload":{
                            "mimeType":"text/html",
                            "headers":[],
                            "body":{"data":"%s"}
                          }
                        }
                        """.formatted(base64Url(htmlBody)), MediaType.APPLICATION_JSON));

        GmailMessageContent message = client.getFullMessage("access-token", "message-2");

        assertTrue(message.body().contains("Order number: ORDER-456"));
        assertTrue(message.body().contains("Total: INR 299.00"));
        assertFalse(message.body().contains("<p>"));
        server.verify();
    }

    @Test
    void ignoresTextAttachmentsWhenSelectingTheMessageBody() {
        String attachment = "Order number: WRONG-999";
        String messageBody = "Order number: ORDER-789";
        server.expect(once(), request ->
                        assertEquals("/gmail/v1/users/me/messages/message-3", request.getURI().getPath()))
                .andRespond(withSuccess("""
                        {
                          "id":"message-3",
                          "payload":{
                            "mimeType":"multipart/mixed",
                            "headers":[],
                            "parts":[
                              {
                                "mimeType":"text/plain",
                                "filename":"invoice.txt",
                                "body":{"data":"%s"}
                              },
                              {
                                "mimeType":"text/plain",
                                "filename":"",
                                "body":{"data":"%s"}
                              }
                            ]
                          }
                        }
                        """.formatted(base64Url(attachment), base64Url(messageBody)), MediaType.APPLICATION_JSON));

        GmailMessageContent message = client.getFullMessage("access-token", "message-3");

        assertEquals(messageBody, message.body());
        server.verify();
    }

    private String base64Url(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
