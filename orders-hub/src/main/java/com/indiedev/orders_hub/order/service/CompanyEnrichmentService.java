package com.indiedev.orders_hub.order.service;

import com.indiedev.orders_hub.order.entity.Company;
import com.indiedev.orders_hub.order.enums.EnrichmentStatusEnum;
import com.indiedev.orders_hub.order.repository.CompanyMasterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CompanyEnrichmentService {

    private final CompanyMasterRepository companyMasterRepository;

    @Transactional
    public void enrich(long companyId) {
        Company company = companyMasterRepository.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company not found with id: " + companyId));

        if(company.getEnrichmentStatus() == null) {
            throw new RuntimeException("Enrichment status not found for company with id: " + companyId);
        }

        company.setEnrichmentStatus(EnrichmentStatusEnum.ENRICHING);
        companyMasterRepository.save(company);
    }
}
