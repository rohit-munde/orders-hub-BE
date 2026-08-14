package com.indiedev.orders_hub.user.service;

import com.indiedev.orders_hub.connectedaccount.repository.ConnectedAccountRepository;
import com.indiedev.orders_hub.order.repository.OrderRepository;
import com.indiedev.orders_hub.user.entity.User;
import com.indiedev.orders_hub.user.repository.UserRepository;
import com.indiedev.orders_hub.user.response.UserDetailsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserDetailsService {
    private final UserRepository userRepository;
    private final ConnectedAccountRepository connectedAccountRepository;
    private final OrderRepository orderRepository;

    @Transactional
    public UserDetailsResponse getUserDetails(long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user was not found"));

        return new UserDetailsResponse(
                user.getName(),
                user.getProfileUrl(),
                connectedAccountRepository.countByUserId(userId),
                orderRepository.countByUserId(userId)
        );
    }
}
