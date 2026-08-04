package com.indiedev.orders_hub.gmail.service;

import com.indiedev.orders_hub.connectedaccount.ConnectedAccount;
import com.indiedev.orders_hub.connectedaccount.ConnectedAccountPersistenceService;
import com.indiedev.orders_hub.exception.GmailConnectionRequiredException;
import com.indiedev.orders_hub.security.token.TokenEncryptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class GoogleAccessTokenService {

    private static final Duration EXPIRY_MARGIN = Duration.ofSeconds(60);

    private final TokenEncryptionService encryptionService;
    private final GoogleOAuthService oAuthService;
    private final ConnectedAccountPersistenceService persistenceService;
    private final Clock clock;

    public String getValidAccessToken(ConnectedAccount account) {
        Instant now = clock.instant();
        if (isReusable(account, now)) {
            String accessToken = decrypt(account.getEncryptedAccessToken());
            if (StringUtils.hasText(accessToken)) {
                return accessToken;
            }
        }

        String refreshToken = decrypt(account.getEncryptedRefreshToken());
        if (!StringUtils.hasText(refreshToken)) {
            throw reconnectRequired();
        }

        GoogleOAuthService.RefreshedToken refreshedToken = oAuthService.refreshAccessToken(refreshToken);
        persistenceService.storeRefreshedAccessToken(account.getId(), refreshedToken, now);
        return refreshedToken.accessToken();
    }

    private boolean isReusable(ConnectedAccount account, Instant now) {
        return StringUtils.hasText(account.getEncryptedAccessToken())
                && account.getAccessTokenExpiresAt() != null
                && account.getAccessTokenExpiresAt().isAfter(now.plus(EXPIRY_MARGIN));
    }

    private String decrypt(String encryptedToken) {
        if (!StringUtils.hasText(encryptedToken)) {
            return null;
        }
        try {
            return encryptionService.decrypt(encryptedToken);
        } catch (RuntimeException exception) {
            throw reconnectRequired();
        }
    }

    private GmailConnectionRequiredException reconnectRequired() {
        return new GmailConnectionRequiredException("Gmail account must be reconnected");
    }
}
