package com.indiedev.orders_hub.gmail.service;

import com.indiedev.orders_hub.connectedaccount.entity.ConnectedAccount;
import com.indiedev.orders_hub.security.token.TokenEncryptionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class GoogleAccessRevocationServiceTest {

    private static final String KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    private final TokenEncryptionService encryptionService = new TokenEncryptionService(KEY);
    private final GoogleOAuthService oAuthService = mock(GoogleOAuthService.class);
    private final GoogleAccessRevocationService service =
            new GoogleAccessRevocationService(encryptionService, oAuthService);

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void revokesTheRefreshTokenImmediatelyOutsideATransaction() {
        service.revokeAfterCommit(List.of(account("refresh-token", "access-token")));

        verify(oAuthService).revokeToken("refresh-token");
    }

    @Test
    void fallsBackToTheAccessTokenWhenNoRefreshTokenIsStored() {
        service.revokeAfterCommit(List.of(account(null, "access-token")));

        verify(oAuthService).revokeToken("access-token");
    }

    @Test
    void waitsForTheTransactionToCommitBeforeRevoking() {
        TransactionSynchronizationManager.initSynchronization();

        service.revokeAfterCommit(List.of(account("refresh-token", null)));

        verify(oAuthService, never()).revokeToken(anyString());
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }
        verify(oAuthService).revokeToken("refresh-token");
    }

    @Test
    void skipsAccountsWhoseTokensCannotBeDecrypted() {
        ConnectedAccount account = new ConnectedAccount();
        account.setEncryptedRefreshToken("not-a-valid-ciphertext");

        service.revokeAfterCommit(List.of(account));

        verify(oAuthService, never()).revokeToken(anyString());
    }

    private ConnectedAccount account(String refreshToken, String accessToken) {
        ConnectedAccount account = new ConnectedAccount();
        account.setEncryptedRefreshToken(encryptionService.encrypt(refreshToken));
        account.setEncryptedAccessToken(encryptionService.encrypt(accessToken));
        return account;
    }
}
