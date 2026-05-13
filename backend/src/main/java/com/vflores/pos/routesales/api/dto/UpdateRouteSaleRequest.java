package com.vflores.pos.routesales.api.dto;

import com.vflores.pos.sales.domain.model.Sale;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record UpdateRouteSaleRequest(
        @NotNull
        UUID clientId,

        @NotNull
        UUID driverId,

        Sale.PaymentMethod paymentMethod,

        @NotEmpty
        List<@Valid RouteSaleItemRequest> items,

        String comments
) {
}
