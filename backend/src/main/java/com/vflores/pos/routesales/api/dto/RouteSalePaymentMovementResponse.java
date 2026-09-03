package com.vflores.pos.routesales.api.dto;

import com.vflores.pos.sales.domain.model.Sale;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RouteSalePaymentMovementResponse(
        UUID id,
        UUID routeSaleId,
        Long invoiceNumber,
        UUID clientId,
        OffsetDateTime routeSaleCreatedAt,
        Sale.PaymentMethod method,
        BigDecimal amount,
        OffsetDateTime createdAt
) {
}
