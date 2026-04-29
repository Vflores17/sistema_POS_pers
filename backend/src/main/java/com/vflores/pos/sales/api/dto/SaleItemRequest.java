package com.vflores.pos.sales.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SaleItemRequest(
        @NotNull
        UUID productId,

        @NotNull
        @Min(value = 1, message = "quantity must be >= 1")
        Integer quantity
) {
}
