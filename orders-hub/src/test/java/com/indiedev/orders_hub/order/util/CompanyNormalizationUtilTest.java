package com.indiedev.orders_hub.order.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompanyNormalizationUtilTest {

    @Test
    void normalizesUrlsEmailsAndBrandNames() {
        assertEquals("amazon.in", CompanyNormalizationUtil.normalizeAliasValue("https://www.amazon.in/order/123"));
        assertEquals("swiggy.in", CompanyNormalizationUtil.normalizeAliasValue("orders@swiggy.in"));
        assertEquals("wint wealth", CompanyNormalizationUtil.normalizeAliasValue("  Wint Wealth  "));
    }
}
