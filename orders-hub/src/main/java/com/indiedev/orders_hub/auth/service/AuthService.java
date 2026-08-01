package com.indiedev.orders_hub.auth.service;

import com.indiedev.orders_hub.auth.response.GoogleUserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final GoogleTokenVerifier googleTokenVerifier;

    public GoogleUserResponse loginWithGoogle(String idToken) {
        if (!StringUtils.hasText(idToken)) {
            throw new IllegalArgumentException("Google ID token is required");
        }

        return googleTokenVerifier.verify(idToken);
    }
}
