package com.indiedev.orders_hub.gmail.service;

import com.indiedev.orders_hub.gmail.client.GmailApiClient;
import com.indiedev.orders_hub.gmail.config.GmailSearchProperties;
import com.indiedev.orders_hub.gmail.dto.GmailMessageContent;
import com.indiedev.orders_hub.gmail.dto.GmailOrderPreview;
import com.indiedev.orders_hub.gmail.dto.GmailSyncPreview;
import com.indiedev.orders_hub.order.OrderStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GmailSyncServiceTest {

    @Test
    void returnsOrderPreviewForTheFirstMatchingMessage() {
        GmailApiClient client = mock(GmailApiClient.class);
        GmailOrderParser parser = mock(GmailOrderParser.class);
        GmailSearchProperties properties = configuredSearch();
        GmailSyncService service = new GmailSyncService(client, properties, parser);
        GmailMessageContent message = new GmailMessageContent(
                "gmail-1", "Order shipped", "Store <orders@store.com>", "Order ID: ABC-123"
        );
        GmailOrderPreview orderPreview = new GmailOrderPreview(
                "gmail-1", "store.com", "Store", "ABC-123", null, null,
                null, null, OrderStatus.SHIPPED, List.of()
        );
        String expectedQuery = "newer_than:1y {subject:order subject:receipt from:amazon.in}";
        when(client.findFirstMessageId("access-token", expectedQuery)).thenReturn(Optional.of("gmail-1"));
        when(client.getFullMessage("access-token", "gmail-1")).thenReturn(message);
        when(parser.parse(message)).thenReturn(orderPreview);

        GmailSyncPreview preview = service.previewFirstOrder("access-token");

        assertEquals(expectedQuery, preview.query());
        assertEquals("gmail-1", preview.gmailMessageId());
        assertSame(orderPreview, preview.orderPreview());
    }

    @Test
    void returnsEmptyPreviewWhenNoMessageMatches() {
        GmailApiClient client = mock(GmailApiClient.class);
        GmailOrderParser parser = mock(GmailOrderParser.class);
        GmailSearchProperties properties = configuredSearch();
        GmailSyncService service = new GmailSyncService(client, properties, parser);
        when(client.findFirstMessageId(anyString(), anyString())).thenReturn(Optional.empty());

        GmailSyncPreview preview = service.previewFirstOrder("access-token");

        assertNull(preview.gmailMessageId());
        assertNull(preview.orderPreview());
        verify(client, never()).getFullMessage(anyString(), anyString());
        verifyNoInteractions(parser);
    }

    @Test
    void rejectsUnsafeSearchConfiguration() {
        GmailSearchProperties properties = new GmailSearchProperties();
        properties.setSubjectKeywords(List.of("order}"));
        properties.setSenderDomains(List.of());

        assertThrows(
                IllegalArgumentException.class,
                () -> new GmailSyncService(
                        mock(GmailApiClient.class), properties, mock(GmailOrderParser.class)
                ).previewFirstOrder("access-token")
        );
    }

    private GmailSearchProperties configuredSearch() {
        GmailSearchProperties properties = new GmailSearchProperties();
        properties.setSubjectKeywords(List.of("order", "receipt"));
        properties.setSenderDomains(List.of("amazon.in"));
        return properties;
    }
}
