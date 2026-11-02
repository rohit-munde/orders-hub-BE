package com.indiedev.orders_hub.order.service;

import com.indiedev.orders_hub.order.entity.Company;
import com.indiedev.orders_hub.order.enums.EnrichmentStatusEnum;
import com.indiedev.orders_hub.order.repository.CompanyMasterRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.*;

class CompanyEnrichmentRetrySchedulerTest {

    @Test
    void retriesPendingCompanies() {
        CompanyMasterRepository repository = mock(CompanyMasterRepository.class);
        CompanyEnrichmentService enrichmentService = mock(CompanyEnrichmentService.class);

        Company company = new Company();
        company.setId(10L);
        when(repository.findTop10ByEnrichmentStatusOrderByUpdatedAtAsc(EnrichmentStatusEnum.PENDING))
                .thenReturn(List.of(company));

        CompanyEnrichmentRetryScheduler scheduler = new CompanyEnrichmentRetryScheduler(repository, enrichmentService);
        scheduler.retryPendingCompanies();

        verify(enrichmentService).enrich(10L);
    }
}
