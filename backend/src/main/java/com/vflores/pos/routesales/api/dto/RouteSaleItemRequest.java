package com.vflores.pos.routesales.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record RouteSaleItemRequest(
        @NotNull
        UUID productId,

        @NotNull
        @DecimalMin(value = "0.001", message = "quantity must be > 0")
        BigDecimal quantity,

        BigDecimal price
) {
}