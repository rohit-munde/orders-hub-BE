package com.indiedev.orders_hub.auth.service;

import com.indiedev.orders_hub.auth.response.AuthResponse;
import com.indiedev.orders_hub.connectedaccount.entity.ConnectedAccount;
import com.indiedev.orders_hub.connectedaccount.entity.ConnectedAccountProvider;
import com.indiedev.orders_hub.connectedaccount.repository.ConnectedAccountRepository;
import com.indiedev.orders_hub.gmail.service.GmailConnectionService;
import com.indiedev.orders_hub.gmail.service.GoogleOAuthService;
import com.indiedev.orders_hub.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final GoogleTokenVerifier googleTokenVerifier;
    private final GoogleOAuthService googleOAuthService;
    private final GoogleUserService googleUserService;
    private final GmailConnectionService gmailConnectionService;
    private final JwtService jwtService;
    private final ConnectedAccountRepository connectedAccountRepository;

    public AuthResponse loginWithGoogle(String idToken, String serverAuthCode) {
        GoogleTokenVerifier.GoogleUser googleUser = googleTokenVerifier.verify(idToken);
        GoogleOAuthService.Token googleToken = googleOAuthService.exchangeAuthorizationCode(serverAuthCode);
        GoogleTokenVerifier.GoogleUser authorizedUser = googleTokenVerifier.verify(googleToken.idToken());
        if (!googleUser.subject().equals(authorizedUser.subject())) {
            throw new IllegalArgumentException("Google credentials do not belong to the same account");
        }

        return connectedAccountRepository.findByProviderAndEmail(
                        ConnectedAccountProvider.GOOGLE,
                        googleUser.email()
                )
                .map(account -> loginWithConnectedAccount(account, googleToken))
                .orElseGet(() -> loginWithPrimaryGoogleAccount(googleUser, googleToken));
    }

    private AuthResponse loginWithConnectedAccount(
            ConnectedAccount account,
            GoogleOAuthService.Token googleToken
    ) {
        User owner = account.getUser();
        GmailConnectionService.ConnectionResult connection = gmailConnectionService.connect(owner, googleToken);
        return response(owner, connection);
    }

    private AuthResponse loginWithPrimaryGoogleAccount(
            GoogleTokenVerifier.GoogleUser googleUser,
            GoogleOAuthService.Token googleToken
    ) {
        User savedUser = googleUserService.createOrUpdate(googleUser);
        GmailConnectionService.ConnectionResult connection = gmailConnectionService.connect(savedUser, googleToken);
        return response(savedUser, connection);
    }

    private AuthResponse response(User user, GmailConnectionService.ConnectionResult connection) {
        return new AuthResponse(
                jwtService.issue(user),
                new AuthResponse.UserInfo(
                        user.getId(),
                        user.getName(),
                        user.getEmail(),
                        user.getProfileUrl()
                ),
                new AuthResponse.ConnectedAccountInfo(
                        connection.accountId(),
                        connection.email(),
                        connection.status()
                ),
                connection.preview()
        );
    }
}
