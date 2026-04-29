package com.vflores.pos.products.api.dto;

import com.vflores.pos.products.domain.model.ProductPriceType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ProductPriceResponse(
        UUID id,
        UUID productId,
        ProductPriceType type,
        BigDecimal price,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
