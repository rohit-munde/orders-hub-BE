package com.indiedev.orders_hub.order.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.junit.jupiter.api.Assertions.*;

class CompanyEnrichmentAsyncConfigTest {

    @Test
    void configuresTaskExecutorProperly() {
        CompanyEnrichmentAsyncConfig config = new CompanyEnrichmentAsyncConfig();
        ThreadPoolTaskExecutor executor = config.companyEnrichmentTaskExecutor();

        assertNotNull(executor);
        assertEquals("company-enrichment-", executor.getThreadNamePrefix());
        assertEquals(1, executor.getCorePoolSize());
        assertEquals(1, executor.getMaxPoolSize());
    }
}
