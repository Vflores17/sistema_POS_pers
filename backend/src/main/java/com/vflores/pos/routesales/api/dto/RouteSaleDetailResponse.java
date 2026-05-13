package com.vflores.pos.routesales.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record RouteSaleDetailResponse(
        UUID productId,
        String productName,
        Integer quantity,
        BigDecimal price,
        BigDecimal subtotal
) {
}
