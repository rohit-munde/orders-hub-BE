package com.indiedev.orders_hub.connectedaccount;

import com.indiedev.orders_hub.gmail.exception.ConnectedAccountConflictException;
import com.indiedev.orders_hub.gmail.exception.RefreshTokenMissingException;
import com.indiedev.orders_hub.gmail.service.GoogleOAuthToken;
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
            GoogleOAuthToken token,
            Instant connectedAt
    ) {
        ConnectedAccount account = connectedAccountRepository
                .findByProviderAndEmail(ConnectedAccountProvider.GOOGLE, email)
                .map(existing -> verifyOwnership(existing, user))
                .orElseGet(() -> newAccount(user, email));

        if (StringUtils.hasText(token.refreshToken())) {
            account.setEncryptedRefreshToken(tokenEncryptionService.encrypt(token.refreshToken()));
        } else if (!StringUtils.hasText(account.getEncryptedRefreshToken())) {
            throw new RefreshTokenMissingException();
        }

        account.setEncryptedAccessToken(tokenEncryptionService.encrypt(token.accessToken()));
        account.setAccessTokenExpiresAt(connectedAt.plusSeconds(token.expiresIn()));
        account.setGrantedScopes(token.scope());
        account.setSyncStatus(ConnectedAccountSyncStatus.SYNCING);
        return connectedAccountRepository.save(account);
    }

    @Transactional
    public void markSyncSucceeded(long accountId, Instant syncedAt) {
        ConnectedAccount account = connectedAccountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalStateException("Connected account disappeared during sync"));
        account.setSyncStatus(ConnectedAccountSyncStatus.SYNCED);
        account.setLastSyncAt(syncedAt);
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
