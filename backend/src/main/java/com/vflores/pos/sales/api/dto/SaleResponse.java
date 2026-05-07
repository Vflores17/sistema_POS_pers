package com.vflores.pos.sales.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import com.vflores.pos.sales.domain.model.Sale;

public record SaleResponse(
        UUID id,
        Long invoiceNumber,
        BigDecimal total,
        UUID userId,
        UUID clientId,
        Sale.PaymentMethod paymentMethod,
        Sale.SaleStatus status,
        OffsetDateTime createdAt,
        List<SaleDetailResponse> details,
        List<SalePaymentResponse> payments
) {
}
