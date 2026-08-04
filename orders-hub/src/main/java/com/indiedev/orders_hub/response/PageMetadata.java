package com.indiedev.orders_hub.response;

import org.springframework.data.domain.Page;

public record PageMetadata(
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious
) {
    public static PageMetadata from(Page<?> page) {
        return new PageMetadata(
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext(),
                page.hasPrevious()
        );
    }
}
