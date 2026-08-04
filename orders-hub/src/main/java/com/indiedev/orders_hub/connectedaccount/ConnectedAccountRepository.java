package com.indiedev.orders_hub.connectedaccount;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.Optional;

public interface ConnectedAccountRepository extends JpaRepository<ConnectedAccount, Long> {

    Optional<ConnectedAccount> findByProviderAndEmail(
            ConnectedAccountProvider provider,
            String email
    );

    @EntityGraph(attributePaths = "user")
    Optional<ConnectedAccount> findFirstByUserIdAndProviderOrderByIdDesc(
            long userId,
            ConnectedAccountProvider provider
    );
}
