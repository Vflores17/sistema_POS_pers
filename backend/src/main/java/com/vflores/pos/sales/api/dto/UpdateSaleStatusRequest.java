package com.vflores.pos.sales.api.dto;

import com.vflores.pos.sales.domain.model.Sale.SaleStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateSaleStatusRequest(
    @NotNull
    SaleStatus status
) {}
