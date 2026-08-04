package com.indiedev.orders_hub.order.service;

import com.indiedev.orders_hub.connectedaccount.ConnectedAccount;
import com.indiedev.orders_hub.connectedaccount.ConnectedAccountPersistenceService;
import com.indiedev.orders_hub.connectedaccount.ConnectedAccountProvider;
import com.indiedev.orders_hub.connectedaccount.ConnectedAccountRepository;
import com.indiedev.orders_hub.gmail.dto.GmailSyncPreview;
import com.indiedev.orders_hub.exception.GmailConnectionRequiredException;
import com.indiedev.orders_hub.exception.GoogleApiException;
import com.indiedev.orders_hub.gmail.service.GmailSyncService;
import com.indiedev.orders_hub.gmail.service.GoogleAccessTokenService;
import com.indiedev.orders_hub.order.dto.OrderSyncResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class OrderSyncServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-03T12:00:00Z");

    private ConnectedAccountRepository accountRepository;
    private ConnectedAccountPersistenceService persistenceService;
    private GoogleAccessTokenService accessTokenService;
    private GmailSyncService gmailSyncService;
    private OrderSyncService service;
    private ConnectedAccount account;

    @BeforeEach
    void setUp() {
        accountRepository = mock(ConnectedAccountRepository.class);
        persistenceService = mock(ConnectedAccountPersistenceService.class);
        accessTokenService = mock(GoogleAccessTokenService.class);
        gmailSyncService = mock(GmailSyncService.class);
        service = new OrderSyncService(
                accountRepository,
                persistenceService,
                accessTokenService,
                gmailSyncService,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        account = new ConnectedAccount();
        account.setId(9);
    }

    @Test
    void rejectsSyncWhenTheUserHasNoConnectedGmailAccount() {
        when(accountRepository.findFirstByUserIdAndProviderOrderByIdDesc(
                7, ConnectedAccountProvider.GOOGLE
        )).thenReturn(Optional.empty());

        GmailConnectionRequiredException exception = assertThrows(
                GmailConnectionRequiredException.class,
                () -> service.sync(7, false)
        );

        assertEquals("Gmail account is not connected", exception.getMessage());
        verifyNoInteractions(persistenceService, accessTokenService, gmailSyncService);
    }

    @Test
    void skipsNetworkWorkDuringTheFifteenMinuteCooldown() {
        Instant lastSyncedAt = NOW.minusSeconds(5 * 60);
        account.setLastSyncAt(lastSyncedAt);
        findAccount();

        OrderSyncResponse response = service.sync(7, false);

        assertEquals(OrderSyncResponse.Outcome.COOLDOWN, response.outcome());
        assertEquals(lastSyncedAt, response.lastSyncedAt());
        assertEquals(0, response.candidateCount());
        assertEquals(0, response.savedCount());
        verifyNoInteractions(persistenceService, accessTokenService, gmailSyncService);
    }

    @Test
    void forcedSyncBypassesCooldownAndReturnsSafeCounts() {
        account.setLastSyncAt(NOW.minusSeconds(5 * 60));
        findAccount();
        when(accessTokenService.getValidAccessToken(account)).thenReturn("access-token");
        when(gmailSyncService.sync(account, "access-token")).thenReturn(
                new GmailSyncPreview("private-query", 6, 2, 1, 2, 1, List.of())
        );

        OrderSyncResponse response = service.sync(7, true);

        assertEquals(OrderSyncResponse.Outcome.COMPLETED, response.outcome());
        assertEquals(NOW, response.lastSyncedAt());
        assertEquals(6, response.candidateCount());
        assertEquals(2, response.savedCount());
        assertEquals(1, response.skippedCount());
        assertEquals(2, response.ignoredCount());
        assertEquals(1, response.failedCount());
        InOrder flow = inOrder(persistenceService, accessTokenService, gmailSyncService);
        flow.verify(persistenceService).markSyncStarted(9);
        flow.verify(accessTokenService).getValidAccessToken(account);
        flow.verify(gmailSyncService).sync(account, "access-token");
        flow.verify(persistenceService).markSyncSucceeded(9, NOW);
    }

    @Test
    void marksSyncFailedWhenGoogleWorkCannotStart() {
        findAccount();
        GoogleApiException failure = new GoogleApiException("Google access token refresh failed");
        when(accessTokenService.getValidAccessToken(account)).thenThrow(failure);

        GoogleApiException thrown = assertThrows(
                GoogleApiException.class,
                () -> service.sync(7, false)
        );

        assertEquals(failure, thrown);
        verify(persistenceService).markSyncStarted(9);
        verify(persistenceService).markSyncFailed(9);
        verifyNoInteractions(gmailSyncService);
    }

    private void findAccount() {
        when(accountRepository.findFirstByUserIdAndProviderOrderByIdDesc(
                7, ConnectedAccountProvider.GOOGLE
        )).thenReturn(Optional.of(account));
    }
}
