package com.indiedev.orders_hub.order.repository;

import com.indiedev.orders_hub.order.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyMasterRepository extends JpaRepository<Company, Long> {
}
