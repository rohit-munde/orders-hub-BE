package com.indiedev.orders_hub.connectedaccount;

import com.indiedev.orders_hub.exception.ConnectedAccountConflictException;
import com.indiedev.orders_hub.gmail.service.GoogleOAuthService;
import com.indiedev.orders_hub.security.token.TokenEncryptionService;
import com.indiedev.orders_hub.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ConnectedAccountPersistenceService {

    private final ConnectedAccountRepository connectedAccountRepository;
    private final TokenEncryptionService tokenEncryptionService;

    @Transactional
    public ConnectedAccount storeConnection(
            User user,
            String email,
            GoogleOAuthService.Token token,
            Instant connectedAt
    ) {
        ConnectedAccount account = connectedAccountRepository
                .findByProviderAndEmail(ConnectedAccountProvider.GOOGLE, email)
                .map(existing -> verifyOwnership(existing, user))
                .orElseGet(() -> newAccount(user, email));

        if (StringUtils.hasText(token.refreshToken())) {
            account.setEncryptedRefreshToken(tokenEncryptionService.encrypt(token.refreshToken()));
        } else if (!StringUtils.hasText(account.getEncryptedRefreshToken())) {
            throw new IllegalArgumentException(
                    "Google did not return a refresh token; revoke access and reconnect with offline consent"
            );
        }

        account.setEncryptedAccessToken(tokenEncryptionService.encrypt(token.accessToken()));
        account.setAccessTokenExpiresAt(connectedAt.plusSeconds(token.expiresIn()));
        account.setGrantedScopes(token.scope());
        account.setSyncStatus(ConnectedAccountSyncStatus.SYNCING);
        return connectedAccountRepository.save(account);
    }

    @Transactional
    public ConnectedAccount markSyncSucceeded(long accountId, Instant syncedAt) {
        ConnectedAccount account = connectedAccountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalStateException("Connected account disappeared during sync"));
        account.setSyncStatus(ConnectedAccountSyncStatus.SYNCED);
        account.setLastSyncAt(syncedAt);
        return account;
    }

    @Transactional
    public void markSyncStarted(long accountId) {
        ConnectedAccount account = connectedAccountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalStateException("Connected account disappeared before sync"));
        account.setSyncStatus(ConnectedAccountSyncStatus.SYNCING);
    }

    @Transactional
    public ConnectedAccount storeRefreshedAccessToken(
            long accountId,
            GoogleOAuthService.RefreshedToken token,
            Instant refreshedAt
    ) {
        ConnectedAccount account = connectedAccountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalStateException("Connected account disappeared during token refresh"));
        account.setEncryptedAccessToken(tokenEncryptionService.encrypt(token.accessToken()));
        account.setAccessTokenExpiresAt(refreshedAt.plusSeconds(token.expiresIn()));
        if (StringUtils.hasText(token.scope())) {
            account.setGrantedScopes(token.scope());
        }
        return account;
    }

    @Transactional
    public void markSyncFailed(long accountId) {
        connectedAccountRepository.findById(accountId).ifPresent(account ->
                account.setSyncStatus(ConnectedAccountSyncStatus.FAILED)
        );
    }

    private ConnectedAccount verifyOwnership(ConnectedAccount account, User user) {
        if (account.getUser().getId() != user.getId()) {
            throw new ConnectedAccountConflictException();
        }
        return account;
    }

    private ConnectedAccount newAccount(User user, String email) {
        ConnectedAccount account = new ConnectedAccount();
        account.setProvider(ConnectedAccountProvider.GOOGLE);
        account.setEmail(email);
        account.setUser(user);
        return account;
    }
}
