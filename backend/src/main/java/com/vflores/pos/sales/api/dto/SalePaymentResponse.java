package com.vflores.pos.sales.api.dto;

import com.vflores.pos.sales.domain.model.Sale;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record SalePaymentResponse(
        UUID id,
        UUID saleId,
        Sale.PaymentMethod method,
        BigDecimal amount,
        OffsetDateTime createdAt
) {
}
