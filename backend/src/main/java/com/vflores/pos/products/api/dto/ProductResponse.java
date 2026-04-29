package com.vflores.pos.products.api.dto;

import com.vflores.pos.products.domain.model.ProductStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        String name,
        BigDecimal price,
        Integer stock,
        ProductStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
