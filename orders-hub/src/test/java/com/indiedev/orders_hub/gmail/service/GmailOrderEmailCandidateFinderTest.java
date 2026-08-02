package com.indiedev.orders_hub.gmail.service;

import com.indiedev.orders_hub.gmail.client.GmailApiClient;
import com.indiedev.orders_hub.gmail.config.GmailSearchProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class GmailOrderEmailCandidateFinderTest {

    @Test
    void buildsConfiguredQueryAndReturnsCandidateBatch() {
        GmailApiClient client = mock(GmailApiClient.class);
        GmailSearchProperties properties = new GmailSearchProperties();
        properties.setSubjectKeywords(List.of("order", "receipt"));
        properties.setSenderDomains(List.of("amazon.in"));
        GmailOrderEmailCandidateFinder finder = new GmailOrderEmailCandidateFinder(client, properties);
        String expectedQuery = "newer_than:1y {subject:order subject:receipt from:amazon.in}";
        when(client.findMessageIds("access-token", expectedQuery, 25))
                .thenReturn(List.of("message-1", "message-2"));

        OrderEmailCandidateFinder.CandidateBatch batch = finder.find("access-token");

        assertEquals(expectedQuery, batch.query());
        assertEquals(List.of("message-1", "message-2"), batch.gmailMessageIds());
    }

    @Test
    void capsConfiguredBatchAtFiftyMessages() {
        GmailApiClient client = mock(GmailApiClient.class);
        GmailSearchProperties properties = new GmailSearchProperties();
        properties.setBatchSize(100);
        when(client.findMessageIds(anyString(), anyString(), eq(50))).thenReturn(List.of());

        new GmailOrderEmailCandidateFinder(client, properties).find("access-token");

        verify(client).findMessageIds(eq("access-token"), anyString(), eq(50));
    }

    @Test
    void rejectsUnsafeFilterConfigurationBeforeCallingGmail() {
        GmailApiClient client = mock(GmailApiClient.class);
        GmailSearchProperties properties = new GmailSearchProperties();
        properties.setSubjectKeywords(List.of("order}"));
        properties.setSenderDomains(List.of());

        assertThrows(
                IllegalArgumentException.class,
                () -> new GmailOrderEmailCandidateFinder(client, properties).find("access-token")
        );
        verifyNoInteractions(client);
    }
}
