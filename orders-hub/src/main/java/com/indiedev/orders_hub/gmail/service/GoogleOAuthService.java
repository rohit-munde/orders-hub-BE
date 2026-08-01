package com.indiedev.orders_hub.gmail.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.indiedev.orders_hub.gmail.config.GoogleOAuthProperties;
import com.indiedev.orders_hub.gmail.exception.GoogleOAuthExchangeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class GoogleOAuthService {

    static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
    private static final Logger LOGGER = LoggerFactory.getLogger(GoogleOAuthService.class);

    private final RestClient restClient;
    private final GoogleOAuthProperties properties;

    public GoogleOAuthService(RestClient.Builder restClientBuilder, GoogleOAuthProperties properties) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
    }

    public GoogleOAuthToken exchangeAuthorizationCode(String serverAuthCode) {
        if (!StringUtils.hasText(serverAuthCode)) {
            throw new IllegalArgumentException("Google server authorization code is required");
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", serverAuthCode);
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());

        try {
            TokenResponse response = restClient.post()
                    .uri(TOKEN_ENDPOINT)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);

            if (response == null || !StringUtils.hasText(response.accessToken())) {
                throw new GoogleOAuthExchangeException("Google OAuth response did not include an access token");
            }

            LOGGER.info(
                    "Google OAuth code exchange succeeded: accessTokenReceived=true, refreshTokenReceived={}",
                    StringUtils.hasText(response.refreshToken())
            );

            return new GoogleOAuthToken(
                    response.accessToken(),
                    response.refreshToken(),
                    response.expiresIn(),
                    response.scope(),
                    response.tokenType()
            );
        } catch (GoogleOAuthExchangeException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new GoogleOAuthExchangeException("Google authorization code exchange failed", exception);
        }
    }

    record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("refresh_token") String refreshToken,
            @JsonProperty("expires_in") long expiresIn,
            String scope,
            @JsonProperty("token_type") String tokenType
    ) {
    }
}
