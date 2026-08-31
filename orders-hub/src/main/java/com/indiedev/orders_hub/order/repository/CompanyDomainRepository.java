package com.indiedev.orders_hub.order.repository;

import com.indiedev.orders_hub.order.entity.CompanyDomain;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanyDomainRepository extends JpaRepository<CompanyDomain, Long> {
    Optional<CompanyDomain> findByDomainNameIgnoreCase(String domainName);
}
