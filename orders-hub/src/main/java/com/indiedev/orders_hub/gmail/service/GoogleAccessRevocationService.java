package com.indiedev.orders_hub.gmail.service;

import com.indiedev.orders_hub.connectedaccount.entity.ConnectedAccount;
import com.indiedev.orders_hub.security.token.TokenEncryptionService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Revokes OrdersHub's Google access for connected accounts that are being removed.
 * <p>
 * Tokens are decrypted while the accounts still exist, but the revocation calls are made only
 * after the surrounding database transaction commits, so a rolled-back deletion never leaves a
 * user with their data still present but their Gmail access silently revoked.
 */
@Service
@RequiredArgsConstructor
public class GoogleAccessRevocationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(GoogleAccessRevocationService.class);

    private final TokenEncryptionService encryptionService;
    private final GoogleOAuthService oAuthService;

    public void revokeAfterCommit(Collection<ConnectedAccount> accounts) {
        List<String> tokens = accounts.stream()
                .map(this::revocableToken)
                .filter(Objects::nonNull)
                .toList();
        if (tokens.isEmpty()) {
            return;
        }

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    revokeAll(tokens);
                }
            });
        } else {
            revokeAll(tokens);
        }
    }

    private void revokeAll(List<String> tokens) {
        tokens.forEach(oAuthService::revokeToken);
    }

    private String revocableToken(ConnectedAccount account) {
        String encrypted = StringUtils.hasText(account.getEncryptedRefreshToken())
                ? account.getEncryptedRefreshToken()
                : account.getEncryptedAccessToken();
        if (!StringUtils.hasText(encrypted)) {
            return null;
        }
        try {
            return encryptionService.decrypt(encrypted);
        } catch (RuntimeException exception) {
            LOGGER.warn("Skipping Google revocation for connected account {}: token unreadable", account.getId());
            return null;
        }
    }
}
