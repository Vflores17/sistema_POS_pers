package com.vflores.pos.sales.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record SaleDetailResponse(
        UUID productId,
        String productName,
        Integer quantity,
        BigDecimal price,
        BigDecimal subtotal
) {
}
