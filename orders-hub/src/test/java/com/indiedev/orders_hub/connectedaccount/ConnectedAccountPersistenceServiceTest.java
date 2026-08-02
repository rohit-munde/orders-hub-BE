package com.indiedev.orders_hub.connectedaccount;

import com.indiedev.orders_hub.gmail.exception.ConnectedAccountConflictException;
import com.indiedev.orders_hub.gmail.service.GoogleOAuthService;
import com.indiedev.orders_hub.security.token.TokenEncryptionService;
import com.indiedev.orders_hub.user.User;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConnectedAccountPersistenceServiceTest {

    @Test
    void preservesStoredRefreshTokenWhenGoogleDoesNotReturnAnotherOne() {
        ConnectedAccountRepository repository = mock(ConnectedAccountRepository.class);
        TokenEncryptionService encryption = mock(TokenEncryptionService.class);
        ConnectedAccountPersistenceService service = new ConnectedAccountPersistenceService(repository, encryption);
        User owner = user(1);
        ConnectedAccount existing = account(owner, "shopper@gmail.com");
        existing.setEncryptedRefreshToken("already-encrypted-refresh-token");
        when(repository.findByProviderAndEmail(ConnectedAccountProvider.GOOGLE, existing.getEmail()))
                .thenReturn(Optional.of(existing));
        when(encryption.encrypt("new-access-token")).thenReturn("encrypted-access-token");
        when(repository.save(existing)).thenReturn(existing);

        ConnectedAccount saved = service.storeConnection(
                owner,
                existing.getEmail(),
                new GoogleOAuthService.Token(
                        "new-access-token", null, 3600, "gmail-scope", "exchanged-id-token"
                ),
                Instant.parse("2026-08-02T00:00:00Z")
        );

        assertEquals("already-encrypted-refresh-token", saved.getEncryptedRefreshToken());
        verify(encryption, never()).encrypt("already-encrypted-refresh-token");
    }

    @Test
    void refusesToReassignGmailAccountToAnotherUser() {
        ConnectedAccountRepository repository = mock(ConnectedAccountRepository.class);
        ConnectedAccount existing = account(user(1), "shopper@gmail.com");
        when(repository.findByProviderAndEmail(ConnectedAccountProvider.GOOGLE, existing.getEmail()))
                .thenReturn(Optional.of(existing));
        ConnectedAccountPersistenceService service = new ConnectedAccountPersistenceService(
                repository, mock(TokenEncryptionService.class)
        );

        assertThrows(
                ConnectedAccountConflictException.class,
                () -> service.storeConnection(
                        user(2), existing.getEmail(),
                        new GoogleOAuthService.Token("access", "refresh", 3600, "scope", "exchanged-id-token"),
                        Instant.now()
                )
        );
        verify(repository, never()).save(any());
    }

    private User user(long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private ConnectedAccount account(User user, String email) {
        ConnectedAccount account = new ConnectedAccount();
        account.setProvider(ConnectedAccountProvider.GOOGLE);
        account.setEmail(email);
        account.setUser(user);
        return account;
    }
}
