package com.indiedev.orders_hub.order.service;

import com.indiedev.orders_hub.connectedaccount.ConnectedAccount;
import com.indiedev.orders_hub.connectedaccount.ConnectedAccountPersistenceService;
import com.indiedev.orders_hub.connectedaccount.ConnectedAccountProvider;
import com.indiedev.orders_hub.connectedaccount.ConnectedAccountRepository;
import com.indiedev.orders_hub.gmail.dto.GmailSyncPreview;
import com.indiedev.orders_hub.gmail.exception.GmailConnectionRequiredException;
import com.indiedev.orders_hub.gmail.service.GmailSyncService;
import com.indiedev.orders_hub.gmail.service.GoogleAccessTokenService;
import com.indiedev.orders_hub.order.dto.OrderSyncResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class OrderSyncService {

    private static final Duration COOLDOWN = Duration.ofMinutes(15);

    private final ConnectedAccountRepository accountRepository;
    private final ConnectedAccountPersistenceService persistenceService;
    private final GoogleAccessTokenService accessTokenService;
    private final GmailSyncService gmailSyncService;
    private final Clock clock;

    public OrderSyncResponse sync(long userId, boolean force) {
        ConnectedAccount account = accountRepository
                .findFirstByUserIdAndProviderOrderByIdDesc(userId, ConnectedAccountProvider.GOOGLE)
                .orElseThrow(() -> new GmailConnectionRequiredException(
                        "Gmail account is not connected"
                ));
        Instant now = clock.instant();
        if (!force && isCoolingDown(account, now)) {
            return OrderSyncResponse.cooldown(account.getLastSyncAt());
        }

        persistenceService.markSyncStarted(account.getId());
        try {
            String accessToken = accessTokenService.getValidAccessToken(account);
            GmailSyncPreview sync = gmailSyncService.sync(account, accessToken);
            persistenceService.markSyncSucceeded(account.getId(), now);
            return completed(sync, now);
        } catch (RuntimeException exception) {
            persistenceService.markSyncFailed(account.getId());
            throw exception;
        }
    }

    private boolean isCoolingDown(ConnectedAccount account, Instant now) {
        return account.getLastSyncAt() != null
                && account.getLastSyncAt().isAfter(now.minus(COOLDOWN));
    }

    private OrderSyncResponse completed(GmailSyncPreview sync, Instant syncedAt) {
        return new OrderSyncResponse(
                OrderSyncResponse.Outcome.COMPLETED,
                syncedAt,
                sync.candidateCount(),
                sync.savedCount(),
                sync.skippedCount(),
                sync.ignoredCount(),
                sync.failedCount()
        );
    }
}
