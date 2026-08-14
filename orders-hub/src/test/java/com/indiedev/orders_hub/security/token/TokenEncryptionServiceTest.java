package com.indiedev.orders_hub.security.token;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class TokenEncryptionServiceTest {

    private final TokenEncryptionService encryptionService = new TokenEncryptionService(
            Base64.getEncoder().encodeToString(new byte[32])
    );

    @Test
    void encryptsWithRandomIvAndDecryptsToken() {
        String firstCiphertext = encryptionService.encrypt("oauth-token");
        String secondCiphertext = encryptionService.encrypt("oauth-token");

        assertNotEquals("oauth-token", firstCiphertext);
        assertNotEquals(firstCiphertext, secondCiphertext);
        assertEquals("oauth-token", encryptionService.decrypt(firstCiphertext));
    }
}
