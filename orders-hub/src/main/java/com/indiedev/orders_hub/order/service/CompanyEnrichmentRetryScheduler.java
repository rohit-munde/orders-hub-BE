package com.indiedev.orders_hub.order.service;

import com.indiedev.orders_hub.order.enums.EnrichmentStatusEnum;
import com.indiedev.orders_hub.order.repository.CompanyMasterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CompanyEnrichmentRetryScheduler {

    private final CompanyMasterRepository companyMasterRepository;
    private final CompanyEnrichmentService companyEnrichmentService;

    @Async("companyEnrichmentTaskExecutor")
    @Scheduled(fixedDelayString = "${company.enrichment.retry-delay-ms:30000}")
    public void retryPendingCompanies() {
        companyMasterRepository.findTop10ByEnrichmentStatusOrderByUpdatedAtAsc(EnrichmentStatusEnum.PENDING)
                .forEach(company -> {
                    try {
                        companyEnrichmentService.enrich(company.getId());
                    } catch (Exception exception) {
                        log.warn("Company enrichment retry failed for company id {}", company.getId(), exception);
                    }
                });
    }
}
