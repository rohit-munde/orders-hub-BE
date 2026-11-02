package com.indiedev.orders_hub.order.validation;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertFalse;

class HttpCompanyLogoValidatorTest {

    @Test
    void rejectsInvalidNonHttpLogoUrl() {
        HttpCompanyLogoValidator validator = new HttpCompanyLogoValidator(RestClient.builder());

        assertFalse(validator.isUsableLogoUrl("ftp://logo.example.com/logo.png"));
        assertFalse(validator.isUsableLogoUrl(null));
        assertFalse(validator.isUsableLogoUrl("   "));
    }

    @Test
    void rejectsSvgLogoUrl() {
        HttpCompanyLogoValidator validator = new HttpCompanyLogoValidator(RestClient.builder());

        assertFalse(validator.isUsableLogoUrl(
                "https://upload.wikimedia.org/wikipedia/commons/a/a9/Amazon_logo.svg"
        ));
    }
}
