package com.indiedev.orders_hub.auth.service;

import com.indiedev.orders_hub.user.User;
import com.indiedev.orders_hub.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GoogleUserService {

    private final UserRepository userRepository;

    @Transactional
    public User createOrUpdate(GoogleTokenVerifier.GoogleUser googleUser) {
        User user = userRepository.findByGoogleId(googleUser.subject())
                .orElseGet(User::new);

        userRepository.findByEmail(googleUser.email())
                .filter(existing -> user.getId() == 0 || existing.getId() != user.getId())
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("A user with this email already exists");
                });

        user.setGoogleId(googleUser.subject());
        user.setEmail(googleUser.email());
        user.setName(googleUser.name());
        user.setProfileUrl(googleUser.pictureUrl());
        return userRepository.save(user);
    }
}
