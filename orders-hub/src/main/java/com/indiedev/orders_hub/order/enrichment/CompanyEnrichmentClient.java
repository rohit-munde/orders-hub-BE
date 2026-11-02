package com.indiedev.orders_hub.order.enrichment;

import com.indiedev.orders_hub.order.entity.Company;

import java.util.Optional;

public interface CompanyEnrichmentClient {

    Optional<CompanyEnrichmentCandidate> enrichCompany(Company company);
}
