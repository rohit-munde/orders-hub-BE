package com.indiedev.orders_hub.security.token;

public interface TokenEncryptionService {

    String encrypt(String plaintext);

    String decrypt(String ciphertext);
}
