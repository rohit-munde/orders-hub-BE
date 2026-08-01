package com.indiedev.orders_hub.gmail.service;

import com.indiedev.orders_hub.connectedaccount.ConnectedAccount;
import com.indiedev.orders_hub.connectedaccount.ConnectedAccountPersistenceService;
import com.indiedev.orders_hub.gmail.client.GmailApiClient;
import com.indiedev.orders_hub.gmail.client.GmailProfile;
import com.indiedev.orders_hub.gmail.dto.GmailConnectResponse;
import com.indiedev.orders_hub.gmail.dto.GmailMessageSummary;
import com.indiedev.orders_hub.user.AuthenticatedUserService;
import com.indiedev.orders_hub.user.User;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GmailConnectionService {

    private static final String REQUIRED_GMAIL_SCOPE =
            "https://www.googleapis.com/auth/gmail.readonly";
    private static final Logger LOGGER = LoggerFactory.getLogger(GmailConnectionService.class);

    private final AuthenticatedUserService authenticatedUserService;
    private final GoogleOAuthService googleOAuthService;
    private final GmailApiClient gmailApiClient;
    private final ConnectedAccountPersistenceService persistenceService;

    public GmailConnectResponse connect(Authentication authentication, String serverAuthCode) {
        User user = authenticatedUserService.requireUser(authentication);
        GoogleOAuthToken token = googleOAuthService.exchangeAuthorizationCode(serverAuthCode);
        verifyGmailScope(token.scope());
        GmailProfile profile = gmailApiClient.getProfile(token.accessToken());
        LOGGER.info("Gmail profile fetch succeeded: emailAddressReceived=true");
        ConnectedAccount account = persistenceService.storeConnection(
                user,
                profile.emailAddress(),
                token,
                Instant.now()
        );

        try {
            List<GmailMessageSummary> messages = gmailApiClient.searchOrderEmails(token.accessToken());
            persistenceService.markSyncSucceeded(account.getId(), Instant.now());
            LOGGER.info(
                    "Initial Gmail search completed: connectedAccountId={}, messageCount={}",
                    account.getId(),
                    messages.size()
            );
            return new GmailConnectResponse(
                    account.getId(),
                    account.getEmail(),
                    messages.size(),
                    messages
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
}
