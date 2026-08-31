package com.indiedev.orders_hub.order.event;

public record CompanyCreatedEvent(
        long companyId,
        String rawBrandName,
        String senderEmail,
        String senderDomain
) {
}
