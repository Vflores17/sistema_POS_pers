package com.vflores.pos.sales.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import com.vflores.pos.sales.domain.model.Sale;

import java.util.List;
import java.util.UUID;

public record UpdateSaleRequest(
        
        @NotNull
        UUID clientId,
        Sale.PaymentMethod paymentMethod,

        @NotEmpty
        List<@Valid SaleItemRequest> items,

        Sale.SaleStatus status
) {
}
