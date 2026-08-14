package com.indiedev.orders_hub.auth.service;

import com.indiedev.orders_hub.auth.response.AuthResponse;
import com.indiedev.orders_hub.connectedaccount.entity.ConnectedAccount;
import com.indiedev.orders_hub.connectedaccount.entity.ConnectedAccountProvider;
import com.indiedev.orders_hub.connectedaccount.repository.ConnectedAccountRepository;
import com.indiedev.orders_hub.gmail.dto.GmailSyncPreview;
import com.indiedev.orders_hub.gmail.service.GmailConnectionService;
import com.indiedev.orders_hub.gmail.service.GoogleOAuthService;
import com.indiedev.orders_hub.user.entity.User;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    @Test
    void validIdTokenConnectsGmailAndReturnsOnlyApplicationToken() {
        GoogleTokenVerifier verifier = mock(GoogleTokenVerifier.class);
        GoogleOAuthService oAuthService = mock(GoogleOAuthService.class);
        GoogleUserService googleUserService = mock(GoogleUserService.class);
        GmailConnectionService connectionService = mock(GmailConnectionService.class);
        JwtService jwtService = mock(JwtService.class);
        ConnectedAccountRepository connectedAccountRepository = mock(ConnectedAccountRepository.class);
        AuthService service = new AuthService(
                verifier, oAuthService, googleUserService, connectionService, jwtService, connectedAccountRepository
        );
        GoogleTokenVerifier.GoogleUser googleUser = new GoogleTokenVerifier.GoogleUser(
                "google-subject", "shopper@gmail.com", "Shopper", "https://picture"
        );
        GoogleOAuthService.Token googleToken = new GoogleOAuthService.Token(
                "google-access-token",
                "google-refresh-token",
                3600,
                "https://www.googleapis.com/auth/gmail.readonly",
                "exchanged-id-token"
        );
        User user = new User();
        user.setId(7);
        user.setName("Shopper");
        user.setEmail("shopper@gmail.com");
        user.setProfileUrl("https://picture");
        when(verifier.verify("google-id-token")).thenReturn(googleUser);
        when(oAuthService.exchangeAuthorizationCode("server-auth-code")).thenReturn(googleToken);
        when(verifier.verify("exchanged-id-token")).thenReturn(googleUser);
        when(connectedAccountRepository.findByProviderAndEmail(
                ConnectedAccountProvider.GOOGLE, "shopper@gmail.com"
        )).thenReturn(Optional.empty());
        when(googleUserService.createOrUpdate(googleUser)).thenReturn(user);
        when(connectionService.connect(user, googleToken)).thenReturn(
                new GmailConnectionService.ConnectionResult(
                        9,
                        "shopper@gmail.com",
                        "SYNCED",
                        new GmailSyncPreview("query", 0, 0, 0, 0, 0, List.of())
                )
        );
        when(jwtService.issue(user)).thenReturn("ordershub-jwt");

        AuthResponse response = service.loginWithGoogle("google-id-token", "server-auth-code");
        String serializedShape = response.toString();

        assertEquals("ordershub-jwt", response.appToken());
        assertFalse(serializedShape.contains("google-id-token"));
        assertFalse(serializedShape.contains("server-auth-code"));
        assertFalse(serializedShape.contains("google-access-token"));
        assertFalse(serializedShape.contains("google-refresh-token"));
    }

    @Test
    void invalidIdTokenStopsBeforeUserOrGmailWork() {
        GoogleTokenVerifier verifier = mock(GoogleTokenVerifier.class);
        GoogleOAuthService oAuthService = mock(GoogleOAuthService.class);
        GoogleUserService googleUserService = mock(GoogleUserService.class);
        GmailConnectionService connectionService = mock(GmailConnectionService.class);
        JwtService jwtService = mock(JwtService.class);
        ConnectedAccountRepository connectedAccountRepository = mock(ConnectedAccountRepository.class);
        when(verifier.verify("invalid-id-token")).thenThrow(new IllegalArgumentException("Invalid Google ID token"));
        AuthService service = new AuthService(
                verifier, oAuthService, googleUserService, connectionService, jwtService, connectedAccountRepository
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.loginWithGoogle("invalid-id-token", "server-auth-code")
        );
        verifyNoInteractions(oAuthService, googleUserService, connectionService, jwtService);
    }

    @Test
    void differentGoogleSubjectsAreRejectedBeforeAnythingIsPersisted() {
        GoogleTokenVerifier verifier = mock(GoogleTokenVerifier.class);
        GoogleOAuthService oAuthService = mock(GoogleOAuthService.class);
        GoogleUserService googleUserService = mock(GoogleUserService.class);
        GmailConnectionService connectionService = mock(GmailConnectionService.class);
        JwtService jwtService = mock(JwtService.class);
        ConnectedAccountRepository connectedAccountRepository = mock(ConnectedAccountRepository.class);
        AuthService service = new AuthService(
                verifier, oAuthService, googleUserService, connectionService, jwtService, connectedAccountRepository
        );
        GoogleOAuthService.Token googleToken = new GoogleOAuthService.Token(
                "google-access-token",
                "google-refresh-token",
                3600,
                "https://www.googleapis.com/auth/gmail.readonly",
                "exchanged-id-token"
        );
        when(verifier.verify("google-id-token")).thenReturn(new GoogleTokenVerifier.GoogleUser(
                "login-subject", "login@gmail.com", "Login User", null
        ));
        when(oAuthService.exchangeAuthorizationCode("server-auth-code")).thenReturn(googleToken);
        when(verifier.verify("exchanged-id-token")).thenReturn(new GoogleTokenVerifier.GoogleUser(
                "authorization-subject", "authorization@gmail.com", "Authorization User", null
        ));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.loginWithGoogle("google-id-token", "server-auth-code")
        );

        assertEquals("Google credentials do not belong to the same account", exception.getMessage());
        verifyNoInteractions(googleUserService, connectionService, jwtService);
    }

    @Test
    void connectedGmailAccountLogsInAsTheOwningApplicationUser() {
        GoogleTokenVerifier verifier = mock(GoogleTokenVerifier.class);
        GoogleOAuthService oAuthService = mock(GoogleOAuthService.class);
        GoogleUserService googleUserService = mock(GoogleUserService.class);
        GmailConnectionService connectionService = mock(GmailConnectionService.class);
        JwtService jwtService = mock(JwtService.class);
        ConnectedAccountRepository connectedAccountRepository = mock(ConnectedAccountRepository.class);
        AuthService service = new AuthService(
                verifier, oAuthService, googleUserService, connectionService, jwtService, connectedAccountRepository
        );
        GoogleTokenVerifier.GoogleUser connectedGoogleUser = new GoogleTokenVerifier.GoogleUser(
                "connected-google-subject", "secondary@gmail.com", "Secondary Account", null
        );
        GoogleOAuthService.Token googleToken = new GoogleOAuthService.Token(
                "google-access-token",
                "google-refresh-token",
                3600,
                "https://www.googleapis.com/auth/gmail.readonly",
                "exchanged-id-token"
        );
        User owner = new User();
        owner.setId(7);
        owner.setName("Primary Owner");
        owner.setEmail("primary@gmail.com");
        owner.setProfileUrl("https://picture");
        ConnectedAccount connectedAccount = new ConnectedAccount();
        connectedAccount.setId(12);
        connectedAccount.setProvider(ConnectedAccountProvider.GOOGLE);
        connectedAccount.setEmail("secondary@gmail.com");
        connectedAccount.setUser(owner);

        when(verifier.verify("google-id-token")).thenReturn(connectedGoogleUser);
        when(oAuthService.exchangeAuthorizationCode("server-auth-code")).thenReturn(googleToken);
        when(verifier.verify("exchanged-id-token")).thenReturn(connectedGoogleUser);
        when(connectedAccountRepository.findByProviderAndEmail(
                ConnectedAccountProvider.GOOGLE, "secondary@gmail.com"
        )).thenReturn(Optional.of(connectedAccount));
        when(connectionService.connect(owner, googleToken)).thenReturn(
                new GmailConnectionService.ConnectionResult(
                        12,
                        "secondary@gmail.com",
                        "SYNCED",
                        new GmailSyncPreview("query", 0, 0, 0, 0, 0, List.of())
                )
        );
        when(jwtService.issue(owner)).thenReturn("owner-jwt");

        AuthResponse response = service.loginWithGoogle("google-id-token", "server-auth-code");

        assertEquals("owner-jwt", response.appToken());
        assertEquals(7, response.user().id());
        assertEquals("primary@gmail.com", response.user().email());
        assertEquals("secondary@gmail.com", response.connectedAccount().email());
        verifyNoInteractions(googleUserService);
    }
}
