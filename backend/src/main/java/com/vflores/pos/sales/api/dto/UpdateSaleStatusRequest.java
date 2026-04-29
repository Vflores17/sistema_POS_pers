package com.vflores.pos.sales.api.dto;

import com.vflores.pos.sales.domain.model.Sale.SaleStatus;

public record UpdateSaleStatusRequest(
    SaleStatus status
) {}