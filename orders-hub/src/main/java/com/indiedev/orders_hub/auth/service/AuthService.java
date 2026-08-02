package com.indiedev.orders_hub.auth.service;

import com.indiedev.orders_hub.auth.response.AuthResponse;
import com.indiedev.orders_hub.gmail.service.GmailConnectionService;
import com.indiedev.orders_hub.gmail.service.GoogleOAuthService;
import com.indiedev.orders_hub.user.User;
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

    public AuthResponse loginWithGoogle(String idToken, String serverAuthCode) {
        GoogleTokenVerifier.GoogleUser googleUser = googleTokenVerifier.verify(idToken);
        GoogleOAuthService.Token googleToken = googleOAuthService.exchangeAuthorizationCode(serverAuthCode);
        GoogleTokenVerifier.GoogleUser authorizedUser = googleTokenVerifier.verify(googleToken.idToken());
        if (!googleUser.subject().equals(authorizedUser.subject())) {
            throw new IllegalArgumentException("Google credentials do not belong to the same account");
        }

        User savedUser = googleUserService.createOrUpdate(googleUser);
        GmailConnectionService.ConnectionResult connection = gmailConnectionService.connect(savedUser, googleToken);

        return new AuthResponse(
                jwtService.issue(savedUser),
                new AuthResponse.UserInfo(
                        savedUser.getId(),
                        savedUser.getName(),
                        savedUser.getEmail(),
                        savedUser.getProfileUrl()
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
