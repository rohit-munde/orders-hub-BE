package com.indiedev.orders_hub.order.service;

import com.indiedev.orders_hub.order.entity.Company;
import com.indiedev.orders_hub.order.repository.CompanyAliasRepository;
import com.indiedev.orders_hub.order.repository.CompanyDomainRepository;
import com.indiedev.orders_hub.order.repository.CompanyMasterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CompanyMasterServiceTest {

    private CompanyMasterRepository companyMasterRepository;
    private CompanyDomainRepository companyDomainRepository;
    private CompanyAliasRepository companyAliasRepository;
    private CompanyMasterService service;

    @BeforeEach
    void setUp() {
        companyMasterRepository = mock(CompanyMasterRepository.class);
        companyDomainRepository = mock(CompanyDomainRepository.class);
        companyAliasRepository = mock(CompanyAliasRepository.class);
        service = new CompanyMasterService(companyMasterRepository, companyDomainRepository, companyAliasRepository);
    }

    @Test
    void reusesExistingCompanyForSameBrandName() {
        Company existing = new Company();
        existing.setId(12);
        existing.setBrandName("Amazon");
        when(companyMasterRepository.findByBrandNameIgnoreCase("Amazon"))
                .thenReturn(Optional.of(existing));

        CompanyMasterService.FindOrCreateCompanyResult result = service.findOrCreateByBrandName(" Amazon.in ");

        assertSame(existing, result.company());
        assertFalse(result.created());
        verify(companyMasterRepository, never()).save(any(Company.class));
    }

    @Test
    void createsCompanyOnceWhenBrandNameDoesNotExist() {
        when(companyMasterRepository.findByBrandNameIgnoreCase("Amazon"))
                .thenReturn(Optional.empty());
        when(companyMasterRepository.saveAndFlush(any(Company.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CompanyMasterService.FindOrCreateCompanyResult result = service.findOrCreateByBrandName(" Amazon.in ");

        assertTrue(result.created());
        assertEquals("Amazon", result.company().getBrandName());
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
