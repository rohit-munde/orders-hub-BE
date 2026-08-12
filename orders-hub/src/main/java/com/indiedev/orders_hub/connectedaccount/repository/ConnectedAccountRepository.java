package com.indiedev.orders_hub.connectedaccount.repository;

import com.indiedev.orders_hub.connectedaccount.entity.ConnectedAccount;
import com.indiedev.orders_hub.connectedaccount.entity.ConnectedAccountProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
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

    @EntityGraph(attributePaths = "user")
    List<ConnectedAccount> findAllByUserIdOrderByIdAsc(long userId);
}
