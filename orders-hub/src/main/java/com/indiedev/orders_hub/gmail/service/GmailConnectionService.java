package com.indiedev.orders_hub.gmail.service;

import com.indiedev.orders_hub.connectedaccount.ConnectedAccount;
import com.indiedev.orders_hub.connectedaccount.ConnectedAccountPersistenceService;
import com.indiedev.orders_hub.gmail.client.GmailApiClient;
import com.indiedev.orders_hub.gmail.dto.GmailSyncPreview;
import com.indiedev.orders_hub.user.User;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Arrays;

@Service
@RequiredArgsConstructor
public class GmailConnectionService {

    private static final String REQUIRED_GMAIL_SCOPE =
            "https://www.googleapis.com/auth/gmail.readonly";
    private static final Logger LOGGER = LoggerFactory.getLogger(GmailConnectionService.class);

    private final GmailApiClient gmailApiClient;
    private final GmailSyncService gmailSyncService;
    private final ConnectedAccountPersistenceService persistenceService;

    public ConnectionResult connect(User user, GoogleOAuthService.Token token) {
        verifyGmailScope(token.scope());
        String gmailAddress = gmailApiClient.getProfileEmail(token.accessToken());
        ConnectedAccount account = persistenceService.storeConnection(
                user,
                gmailAddress,
                token,
                Instant.now()
        );

        try {
            GmailSyncPreview preview = gmailSyncService.sync(account, token.accessToken());
            Instant syncedAt = Instant.now();
            account = persistenceService.markSyncSucceeded(account.getId(), syncedAt);
            LOGGER.info(
                    "Initial Gmail order import completed: connectedAccountId={}, candidates={}, saved={}, failed={}",
                    account.getId(),
                    preview.candidateCount(),
                    preview.savedCount(),
                    preview.failedCount()
            );
            return new ConnectionResult(
                    account.getId(),
                    account.getEmail(),
                    account.getSyncStatus().name(),
                    preview
            );
        } catch (RuntimeException exception) {
            persistenceService.markSyncFailed(account.getId());
            throw exception;
        }
    }

    private void verifyGmailScope(String grantedScopes) {
        boolean granted = grantedScopes != null && Arrays.stream(grantedScopes.split("\\s+"))
                .anyMatch(REQUIRED_GMAIL_SCOPE::equals);
        if (!granted) {
            throw new IllegalArgumentException("Google did not grant Gmail read-only access");
        }
    }

    public record ConnectionResult(
            long accountId,
            String email,
            String status,
            GmailSyncPreview preview
    ) {
    }
}
