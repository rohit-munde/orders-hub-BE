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

    @Test
    void dropsLegalSuffixesFromDisplayedMerchantNames() {
        assertEquals("Cases Villa", CompanyNormalizationUtil.toDisplayBrandName("Cases Villa Private Limited"));
        assertEquals("Myntra Designs", CompanyNormalizationUtil.toDisplayBrandName("Myntra Designs Pvt. Ltd."));
        assertEquals("Acme", CompanyNormalizationUtil.toDisplayBrandName("Acme Pvt Ltd"));
        assertEquals("Swiggy", CompanyNormalizationUtil.toDisplayBrandName("Swiggy Limited"));
        assertEquals("Foo India", CompanyNormalizationUtil.toDisplayBrandName("Foo India, LLP"));
        assertEquals("Apple", CompanyNormalizationUtil.toDisplayBrandName("Apple Inc."));
    }

    @Test
    void keepsNamesThatAreNotLegalSuffixes() {
        assertEquals("Amazon", CompanyNormalizationUtil.toDisplayBrandName("Amazon"));
        assertEquals("Dominos India", CompanyNormalizationUtil.toDisplayBrandName("Dominos India"));
        assertEquals("Limited Edition Store", CompanyNormalizationUtil.toDisplayBrandName("Limited Edition Store"));
        assertEquals("Limited", CompanyNormalizationUtil.toDisplayBrandName("Limited"));
        assertEquals(null, CompanyNormalizationUtil.toDisplayBrandName(null));
    }
}
