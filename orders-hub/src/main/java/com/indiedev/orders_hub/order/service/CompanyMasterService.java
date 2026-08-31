package com.indiedev.orders_hub.order.service;

import com.indiedev.orders_hub.order.entity.Company;
import com.indiedev.orders_hub.order.repository.CompanyMasterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class CompanyMasterService {

    private final CompanyMasterRepository companyMasterRepository;

    @Transactional
    public FindOrCreateCompanyResult findOrCreateByBrandName(String brandName) {
        if (!StringUtils.hasText(brandName)) {
            return new FindOrCreateCompanyResult(null, false);
        }

        String normalizedBrandName = brandName.strip();
        return companyMasterRepository.findByBrandNameIgnoreCase(normalizedBrandName)
                .map(company -> new FindOrCreateCompanyResult(company, false))
                .orElseGet(() -> createCompany(normalizedBrandName));
    }

    private FindOrCreateCompanyResult createCompany(String brandName) {
        Company company = new Company();
        company.setBrandName(brandName);
        try {
            return new FindOrCreateCompanyResult(companyMasterRepository.saveAndFlush(company), true);
        } catch (DataIntegrityViolationException ex) {
            return companyMasterRepository.findByBrandNameIgnoreCase(brandName)
                    .map(existing -> new FindOrCreateCompanyResult(existing, false))
                    .orElseThrow(() -> ex);
        }
    }

    public record FindOrCreateCompanyResult(Company company, boolean created) {
    }
}
