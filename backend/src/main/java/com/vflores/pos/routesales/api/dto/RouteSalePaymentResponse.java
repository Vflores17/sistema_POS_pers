package com.vflores.pos.routesales.api.dto;

import com.vflores.pos.sales.domain.model.Sale;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RouteSalePaymentResponse(
        UUID id,
        UUID routeSaleId,
        Sale.PaymentMethod method,
        BigDecimal amount,
        OffsetDateTime createdAt
) {
}
