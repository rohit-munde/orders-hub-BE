package com.indiedev.orders_hub.connectedaccount.service;

import com.indiedev.orders_hub.connectedaccount.entity.ConnectedAccount;
import com.indiedev.orders_hub.connectedaccount.repository.ConnectedAccountRepository;
import com.indiedev.orders_hub.gmail.service.GmailConnectionService;
import com.indiedev.orders_hub.gmail.service.GoogleOAuthService;
import com.indiedev.orders_hub.connectedaccount.response.ConnectedAccountResponse;
import com.indiedev.orders_hub.user.entity.User;
import com.indiedev.orders_hub.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ConnectedAccountService {
    private final UserRepository userRepository;
    private final GoogleOAuthService googleOAuthService;
    private final GmailConnectionService gmailConnectionService;
    private final ConnectedAccountRepository connectedAccountRepository;

    public ConnectedAccountResponse connectGoogle(long userId, String serverAuthCode) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user was not found"));

        GoogleOAuthService.Token token =
                googleOAuthService.exchangeAuthorizationCode(serverAuthCode);

        GmailConnectionService.ConnectionResult connection =
                gmailConnectionService.connect(user, token);

        return new ConnectedAccountResponse(
                connection.accountId(),
                "GOOGLE",
                connection.email(),
                connection.status(),
                null
        );
    }

    @Transactional(readOnly = true)
    public List<ConnectedAccountResponse> getConnectedAccounts(long userId) {
        return connectedAccountRepository.findAllByUserIdOrderByIdAsc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private ConnectedAccountResponse toResponse(ConnectedAccount account) {
        return new ConnectedAccountResponse(
                account.getId(),
                account.getProvider().name(),
                account.getEmail(),
                account.getSyncStatus().name(),
                account.getLastSyncAt()
        );
    }
}
