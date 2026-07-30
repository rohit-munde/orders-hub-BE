package com.indiedev.orders_hub.common;

import jakarta.persistence.Column;

import java.time.LocalDateTime;

public class BaseEnity {

//    @Column(nullable = true, updatable = false)
    private LocalDateTime createdAt;

//    @Column(nullable = true)
    private LocalDateTime updatedAt;
}
