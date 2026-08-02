package com.indiedev.orders_hub.gmail.service;

import com.indiedev.orders_hub.gmail.client.GmailApiClient;
import com.indiedev.orders_hub.gmail.config.GmailSearchProperties;
import com.indiedev.orders_hub.gmail.dto.GmailMessageSummary;
import com.indiedev.orders_hub.gmail.dto.GmailSyncPreview;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GmailSyncServiceTest {

    @Test
    void loginPreviewFetchesOnlyFirstPageAndReportsAnotherPage() {
        GmailApiClient client = mock(GmailApiClient.class);
        GmailSearchProperties properties = new GmailSearchProperties();
        properties.setMaxResults(25);
        GmailSyncService service = new GmailSyncService(client, properties);

        when(client.listMessageIds(anyString(), anyString(), eq(25)))
                .thenReturn(new GmailApiClient.MessagePage(List.of("gmail-1"), true));
        when(client.getMessageMetadata("access-token", "gmail-1"))
                .thenReturn(new GmailMessageSummary(
                        "gmail-1", "thread-1", "Order shipped", "store@example.com", "today", "<id>"
                ));

        GmailSyncPreview preview = service.previewFirstPage("access-token");

        assertEquals(1, preview.messageCount());
        assertTrue(preview.nextPageTokenAvailable());
        verify(client).listMessageIds(anyString(), anyString(), anyInt());
    }

    @Test
    void buildsAReadableServerSideQueryFromConfiguration() {
        GmailApiClient client = mock(GmailApiClient.class);
        GmailSearchProperties properties = new GmailSearchProperties();
        properties.setSubjectKeywords(List.of("order", "receipt"));
        properties.setSenderDomains(List.of("amazon.in", "flipkart.com"));
        when(client.listMessageIds(anyString(), anyString(), anyInt()))
                .thenReturn(new GmailApiClient.MessagePage(List.of(), false));

        new GmailSyncService(client, properties).previewFirstPage("access-token");

        verify(client).listMessageIds(
                "access-token",
                "newer_than:1y {subject:order subject:receipt from:amazon.in from:flipkart.com}",
                25
        );
    }

    @Test
    void rejectsUnsafeSearchConfiguration() {
        GmailSearchProperties properties = new GmailSearchProperties();
        properties.setSubjectKeywords(List.of("order}"));
        properties.setSenderDomains(List.of());

        assertThrows(
                IllegalArgumentException.class,
                () -> new GmailSyncService(mock(GmailApiClient.class), properties)
                        .previewFirstPage("access-token")
        );
    }

    @Test
    void capsTheLoginPreviewAtTwentyFiveMessages() {
        GmailApiClient client = mock(GmailApiClient.class);
        GmailSearchProperties properties = new GmailSearchProperties();
        properties.setMaxResults(100);
        when(client.listMessageIds(anyString(), anyString(), anyInt()))
                .thenReturn(new GmailApiClient.MessagePage(List.of(), false));

        new GmailSyncService(client, properties).previewFirstPage("access-token");

        verify(client).listMessageIds(eq("access-token"), anyString(), eq(25));
    }
}
