package com.indiedev.orders_hub.auth.service;

import com.indiedev.orders_hub.auth.response.AuthResponse;
import com.indiedev.orders_hub.auth.response.GoogleUserResponse;
import com.indiedev.orders_hub.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final GoogleTokenVerifier googleTokenVerifier;
    private final AuthPersistenceService authPersistenceService;
    private final JwtService jwtService;

    public AuthResponse loginWithGoogle(String idToken) {
        if (!StringUtils.hasText(idToken)) {
            throw new IllegalArgumentException("Google ID token is required");
        }

        GoogleUserResponse googleUser = googleTokenVerifier.verify(idToken);
        User savedUser = authPersistenceService.createOrUpdateGoogleUser(googleUser);
        JwtService.IssuedToken token = jwtService.issue(savedUser);

        return new AuthResponse(
                token.value(),
                "Bearer",
                token.expiresIn(),
                googleUser
        );
    }
}
