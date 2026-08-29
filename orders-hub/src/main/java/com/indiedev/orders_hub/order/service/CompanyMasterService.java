package com.indiedev.orders_hub.order.service;

import com.indiedev.orders_hub.order.repository.CompanyMasterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CompanyMasterService {

    private final CompanyMasterRepository companyMasterRepository;


}
