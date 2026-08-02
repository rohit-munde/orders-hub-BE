package com.indiedev.orders_hub.gmail.service;

import com.indiedev.orders_hub.gmail.config.GoogleOAuthProperties;
import com.indiedev.orders_hub.gmail.exception.GoogleApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GoogleOAuthServiceTest {

    private MockRestServiceServer server;
    private GoogleOAuthService googleOAuthService;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        googleOAuthService = new GoogleOAuthService(
                builder,
                new GoogleOAuthProperties(
                        "web-client-id",
                        "web-client-secret"
                )
        );
    }

    @Test
    void exchangesAuthorizationCodeAndMapsGoogleTokenResponse() {
        MultiValueMap<String, String> expectedForm = new LinkedMultiValueMap<>();
        expectedForm.add("grant_type", "authorization_code");
        expectedForm.add("code", "server-auth-code");
        expectedForm.add("client_id", "web-client-id");
        expectedForm.add("client_secret", "web-client-secret");

        server.expect(once(), requestTo(GoogleOAuthService.TOKEN_ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().formData(expectedForm))
                .andRespond(withSuccess("""
                        {
                          "access_token": "access-token",
                          "refresh_token": "refresh-token",
                          "id_token": "exchanged-id-token",
                          "expires_in": 3600,
                          "scope": "openid https://www.googleapis.com/auth/gmail.readonly",
                          "token_type": "Bearer"
                        }
                        """, MediaType.APPLICATION_JSON));

        GoogleOAuthService.Token token = googleOAuthService.exchangeAuthorizationCode("server-auth-code");

        assertEquals("access-token", token.accessToken());
        assertEquals("refresh-token", token.refreshToken());
        assertEquals("exchanged-id-token", token.idToken());
        assertEquals(3600, token.expiresIn());
        server.verify();
    }

    @Test
    void acceptsResponseWithoutRefreshTokenSoExistingConnectionCanRetainItsToken() {
        server.expect(requestTo(GoogleOAuthService.TOKEN_ENDPOINT))
                .andRespond(withSuccess("""
                        {
                          "access_token": "access-token",
                          "id_token": "exchanged-id-token",
                          "expires_in": 3600,
                          "scope": "https://www.googleapis.com/auth/gmail.readonly",
                          "token_type": "Bearer"
                        }
                        """, MediaType.APPLICATION_JSON));

        GoogleOAuthService.Token token = googleOAuthService.exchangeAuthorizationCode("server-auth-code");

        assertNull(token.refreshToken());
        server.verify();
    }

    @Test
    void rejectsResponseWithoutIdTokenBecauseTheGoogleIdentityCannotBeBound() {
        server.expect(requestTo(GoogleOAuthService.TOKEN_ENDPOINT))
                .andRespond(withSuccess("""
                        {
                          "access_token": "access-token",
                          "expires_in": 3600,
                          "scope": "https://www.googleapis.com/auth/gmail.readonly"
                        }
                        """, MediaType.APPLICATION_JSON));

        GoogleApiException exception = assertThrows(
                GoogleApiException.class,
                () -> googleOAuthService.exchangeAuthorizationCode("server-auth-code")
        );

        assertEquals("Google OAuth response did not include an ID token", exception.getMessage());
        server.verify();
    }

    @Test
    void convertsGoogleHttpFailureToSafeApiException() {
        server.expect(requestTo(GoogleOAuthService.TOKEN_ENDPOINT))
                .andRespond(withResourceNotFound());

        assertThrows(
                GoogleApiException.class,
                () -> googleOAuthService.exchangeAuthorizationCode("invalid-code")
        );
        server.verify();
    }
}
