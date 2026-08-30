package com.indiedev.orders_hub.order.listners;

import com.indiedev.orders_hub.order.event.CompanyCreatedEvent;
import com.indiedev.orders_hub.order.service.CompanyEnrichmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

@Slf4j
@Component
@RequiredArgsConstructor
public class CompanyEnrichmentEventListener {
    private final CompanyEnrichmentService companyEnrichmentService;

    @Async
    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void onCompanyCreated(CompanyCreatedEvent event) {
        try{
            companyEnrichmentService.enrich(event.companyId());
        } catch (Exception e) {
            log.warn("Error enriching company with ID {}", event.companyId(), e);
        }
    }
}
