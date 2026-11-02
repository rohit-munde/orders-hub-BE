package com.indiedev.orders_hub.order.repository;

import com.indiedev.orders_hub.order.entity.Company;
import com.indiedev.orders_hub.order.enums.EnrichmentStatusEnum;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompanyMasterRepository extends JpaRepository<Company, Long> {

    Optional<Company> findByBrandNameIgnoreCase(String brandName);

    List<Company> findTop10ByEnrichmentStatusOrderByUpdatedAtAsc(EnrichmentStatusEnum enrichmentStatus);
}
