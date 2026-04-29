package com.vflores.pos.sales.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record CreateSaleRequest(
        @NotNull
        UUID userId,
        UUID clientId,

        @NotEmpty
        List<@Valid SaleItemRequest> items
) {
}
