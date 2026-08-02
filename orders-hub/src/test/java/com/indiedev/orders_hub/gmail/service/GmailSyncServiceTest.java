package com.indiedev.orders_hub.gmail.service;

import com.indiedev.orders_hub.connectedaccount.ConnectedAccount;
import com.indiedev.orders_hub.gmail.client.GmailApiClient;
import com.indiedev.orders_hub.gmail.dto.GmailMessageContent;
import com.indiedev.orders_hub.gmail.dto.GmailOrderPreview;
import com.indiedev.orders_hub.gmail.dto.GmailSyncPreview;
import com.indiedev.orders_hub.gmail.exception.GoogleApiException;
import com.indiedev.orders_hub.order.Order;
import com.indiedev.orders_hub.order.OrderStatus;
import com.indiedev.orders_hub.order.service.GmailOrderImportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.indiedev.orders_hub.order.service.GmailOrderImportService.Outcome.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GmailSyncServiceTest {

    private OrderEmailCandidateFinder finder;
    private GmailApiClient gmailApiClient;
    private GmailOrderParser parser;
    private GmailOrderImportService importService;
    private GmailSyncService service;
    private ConnectedAccount account;

    @BeforeEach
    void setUp() {
        finder = mock(OrderEmailCandidateFinder.class);
        gmailApiClient = mock(GmailApiClient.class);
        parser = mock(GmailOrderParser.class);
        importService = mock(GmailOrderImportService.class);
        service = new GmailSyncService(finder, gmailApiClient, parser, importService);
        account = new ConnectedAccount();
        account.setId(11);
        when(parser.version()).thenReturn(1);
    }

    @Test
    void continuesMixedBatchWhenMessagesAreSkippedOrFail() {
        when(finder.find("access-token")).thenReturn(new OrderEmailCandidateFinder.CandidateBatch(
                "subject:order", List.of("failed", "saved", "skipped")
        ));
        when(importService.shouldProcess(11, "saved", 1)).thenReturn(true);
        when(importService.shouldProcess(11, "skipped", 1)).thenReturn(false);
        when(importService.shouldProcess(11, "failed", 1)).thenReturn(true);
        GmailMessageContent content = new GmailMessageContent(
                "saved", "Order shipped", "Amazon <orders@amazon.in>", "Order ID: ORDER-1"
        );
        GmailOrderPreview preview = candidate("saved", "ORDER-1");
        when(gmailApiClient.getFullMessage("access-token", "saved")).thenReturn(content);
        when(parser.parse(content)).thenReturn(preview);
        when(importService.importOrder(account, preview, 1))
                .thenReturn(new GmailOrderImportService.ImportResult(SAVED, new Order()));
        when(gmailApiClient.getFullMessage("access-token", "failed"))
                .thenThrow(new GoogleApiException("Unable to fetch Gmail message"));

        GmailSyncPreview result = service.sync(account, "access-token");

        assertEquals(3, result.candidateCount());
        assertEquals(1, result.savedCount());
        assertEquals(1, result.skippedCount());
        assertEquals(0, result.ignoredCount());
        assertEquals(1, result.failedCount());
        assertEquals(List.of(preview), result.orders());
        verify(gmailApiClient, never()).getFullMessage("access-token", "skipped");
        verify(importService).recordFailure(account, "failed", 1);
    }

    @Test
    void countsInvalidParsedCandidateAsIgnored() {
        when(finder.find("access-token")).thenReturn(new OrderEmailCandidateFinder.CandidateBatch(
                "subject:order", List.of("ignored")
        ));
        when(importService.shouldProcess(11, "ignored", 1)).thenReturn(true);
        GmailMessageContent content = new GmailMessageContent(
                "ignored", "Sale", "Amazon <offers@amazon.in>", "Save on your next purchase"
        );
        GmailOrderPreview preview = candidate("ignored", null);
        when(gmailApiClient.getFullMessage("access-token", "ignored")).thenReturn(content);
        when(parser.parse(content)).thenReturn(preview);
        when(importService.importOrder(account, preview, 1))
                .thenReturn(new GmailOrderImportService.ImportResult(IGNORED, null));

        GmailSyncPreview result = service.sync(account, "access-token");

        assertEquals(1, result.candidateCount());
        assertEquals(0, result.savedCount());
        assertEquals(1, result.ignoredCount());
        assertEquals(List.of(), result.orders());
    }

    private GmailOrderPreview candidate(String gmailMessageId, String orderNo) {
        return new GmailOrderPreview(
                gmailMessageId,
                "amazon.in",
                "Amazon",
                orderNo,
                null,
                null,
                null,
                null,
                OrderStatus.SHIPPED,
                List.of()
        );
    }
}
