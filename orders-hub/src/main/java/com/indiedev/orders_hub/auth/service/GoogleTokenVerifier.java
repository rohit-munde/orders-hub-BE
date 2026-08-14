package com.indiedev.orders_hub.auth.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

@Component
public class GoogleTokenVerifier {

    private final GoogleIdTokenVerifier verifier;

    public GoogleTokenVerifier(
            @Value("${google.oauth.client-id}") String webClientId
    ) {
        this.verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance()
        )
                .setAudience(Collections.singletonList(webClientId))
                .build();
    }

    public GoogleUser verify(String idToken) {
        try {
            GoogleIdToken verifiedToken = verifier.verify(idToken);

            if (verifiedToken == null) {
                throw new IllegalArgumentException("Invalid Google ID token");
            }

            GoogleIdToken.Payload payload = verifiedToken.getPayload();
            if (!StringUtils.hasText(payload.getSubject())
                    || !StringUtils.hasText(payload.getEmail())
                    || !Boolean.TRUE.equals(payload.getEmailVerified())) {
                throw new IllegalArgumentException("Invalid Google ID token");
            }

            String name = (String) payload.get("name");
            if (!StringUtils.hasText(name)) {
                name = payload.getEmail();
            }

            return new GoogleUser(
                    payload.getSubject(),
                    payload.getEmail(),
                    name,
                    (String) payload.get("picture")
            );

        } catch (GeneralSecurityException | IOException | IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Invalid Google ID token",
                    exception
            );
        }
    }

    public record GoogleUser(String subject, String email, String name, String pictureUrl) {
    }
}
