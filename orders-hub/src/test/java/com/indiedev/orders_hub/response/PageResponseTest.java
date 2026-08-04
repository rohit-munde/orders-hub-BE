package com.indiedev.orders_hub.response;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PageResponseTest {

    @Test
    void createsStableMetadataFromSpringPage() {
        Page<String> page = new PageImpl<>(
                List.of("order-3", "order-2"),
                PageRequest.of(1, 2),
                5
        );

        PageResponse<String> response = PageResponse.from(page);

        assertEquals(List.of("order-3", "order-2"), response.content());
        assertEquals(new PageMetadata(1, 2, 5, 3, true, true), response.pagination());
    }

    @Test
    void preventsResponseContentFromBeingModified() {
        PageResponse<String> response = PageResponse.from(new PageImpl<>(List.of("order-1")));

        assertThrows(UnsupportedOperationException.class, () -> response.content().add("order-2"));
    }
}
