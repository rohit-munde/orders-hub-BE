package com.indiedev.orders_hub.order.repository;

import com.indiedev.orders_hub.order.entity.CompanyAlias;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanyAliasRepository extends JpaRepository<CompanyAlias, Long> {

    Optional<CompanyAlias> findByNormalizedAliasValue(String normalizedAliasValue);
}
