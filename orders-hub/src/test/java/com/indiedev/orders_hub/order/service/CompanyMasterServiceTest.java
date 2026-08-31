package com.indiedev.orders_hub.order.service;

import com.indiedev.orders_hub.order.entity.Company;
import com.indiedev.orders_hub.order.repository.CompanyMasterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CompanyMasterServiceTest {

    private CompanyMasterRepository companyMasterRepository;
    private CompanyMasterService service;

    @BeforeEach
    void setUp() {
        companyMasterRepository = mock(CompanyMasterRepository.class);
        service = new CompanyMasterService(companyMasterRepository);
    }

    @Test
    void reusesExistingCompanyForSameBrandName() {
        Company existing = new Company();
        existing.setId(12);
        existing.setBrandName("Amazon.in");
        when(companyMasterRepository.findByBrandNameIgnoreCase("Amazon.in"))
                .thenReturn(Optional.of(existing));

        CompanyMasterService.FindOrCreateCompanyResult result = service.findOrCreateByBrandName(" Amazon.in ");

        assertSame(existing, result.company());
        assertFalse(result.created());
        verify(companyMasterRepository, never()).save(any(Company.class));
    }

    @Test
    void createsCompanyOnceWhenBrandNameDoesNotExist() {
        when(companyMasterRepository.findByBrandNameIgnoreCase("Amazon.in"))
                .thenReturn(Optional.empty());
        when(companyMasterRepository.saveAndFlush(any(Company.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CompanyMasterService.FindOrCreateCompanyResult result = service.findOrCreateByBrandName(" Amazon.in ");

        assertTrue(result.created());
        assertEquals("Amazon.in", result.company().getBrandName());
        verify(companyMasterRepository).saveAndFlush(result.company());
    }

    @Test
    void returnsNoCompanyWhenBrandNameIsMissing() {
        CompanyMasterService.FindOrCreateCompanyResult nullResult = service.findOrCreateByBrandName(null);
        CompanyMasterService.FindOrCreateCompanyResult blankResult = service.findOrCreateByBrandName("   ");

        assertNull(nullResult.company());
        assertFalse(nullResult.created());
        assertNull(blankResult.company());
        assertFalse(blankResult.created());
        verifyNoInteractions(companyMasterRepository);
    }
}
