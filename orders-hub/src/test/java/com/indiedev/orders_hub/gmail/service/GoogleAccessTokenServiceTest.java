package com.indiedev.orders_hub.gmail.service;

import com.indiedev.orders_hub.connectedaccount.ConnectedAccount;
import com.indiedev.orders_hub.connectedaccount.ConnectedAccountPersistenceService;
import com.indiedev.orders_hub.gmail.exception.GmailConnectionRequiredException;
import com.indiedev.orders_hub.security.token.TokenEncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class GoogleAccessTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-03T12:00:00Z");

    private TokenEncryptionService encryptionService;
    private GoogleOAuthService oAuthService;
    private ConnectedAccountPersistenceService persistenceService;
    private GoogleAccessTokenService service;

    @BeforeEach
    void setUp() {
        encryptionService = mock(TokenEncryptionService.class);
        oAuthService = mock(GoogleOAuthService.class);
        persistenceService = mock(ConnectedAccountPersistenceService.class);
        service = new GoogleAccessTokenService(
                encryptionService,
                oAuthService,
                persistenceService,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void reusesAnAccessTokenThatHasMoreThanSixtySecondsRemaining() {
        ConnectedAccount account = account();
        account.setEncryptedAccessToken("encrypted-access-token");
        account.setAccessTokenExpiresAt(NOW.plusSeconds(61));
        when(encryptionService.decrypt("encrypted-access-token")).thenReturn("valid-access-token");

        String accessToken = service.getValidAccessToken(account);

        assertEquals("valid-access-token", accessToken);
        verifyNoInteractions(oAuthService, persistenceService);
        verify(encryptionService).decrypt("encrypted-access-token");
    }

    @Test
    void refreshesATokenWithOnlySixtySecondsRemaining() {
        ConnectedAccount account = account();
        account.setEncryptedAccessToken("encrypted-old-access-token");
        account.setAccessTokenExpiresAt(NOW.plusSeconds(60));
        account.setEncryptedRefreshToken("encrypted-refresh-token");
        when(encryptionService.decrypt("encrypted-refresh-token")).thenReturn("stored-refresh-token");
        GoogleOAuthService.RefreshedToken refreshed = new GoogleOAuthService.RefreshedToken(
                "refreshed-access-token", 3600, null
        );
        when(oAuthService.refreshAccessToken("stored-refresh-token")).thenReturn(refreshed);

        String accessToken = service.getValidAccessToken(account);

        assertEquals("refreshed-access-token", accessToken);
        verify(persistenceService).storeRefreshedAccessToken(9, refreshed, NOW);
        verify(encryptionService, never()).decrypt("encrypted-old-access-token");
    }

    @Test
    void asksForReconnectionWhenNoRefreshTokenIsStored() {
        ConnectedAccount account = account();
        account.setAccessTokenExpiresAt(NOW.minusSeconds(1));

        GmailConnectionRequiredException exception = assertThrows(
                GmailConnectionRequiredException.class,
                () -> service.getValidAccessToken(account)
        );

        assertEquals("Gmail account must be reconnected", exception.getMessage());
        assertFalse(exception.getMessage().contains("token"));
        verifyNoInteractions(encryptionService, oAuthService, persistenceService);
    }

    private ConnectedAccount account() {
        ConnectedAccount account = new ConnectedAccount();
        account.setId(9);
        return account;
    }
}
