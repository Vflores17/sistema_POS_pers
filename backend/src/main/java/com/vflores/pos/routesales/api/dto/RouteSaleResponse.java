package com.vflores.pos.routesales.api.dto;

import com.vflores.pos.routesales.domain.model.RouteSale;
import com.vflores.pos.sales.domain.model.Sale;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record RouteSaleResponse(
        UUID id,
        Long invoiceNumber,
        String invoiceLabel,
        UUID userId,
        UUID clientId,
        UUID driverId,
        Sale.PaymentMethod paymentMethod,
        BigDecimal total,
        RouteSale.RouteStatus status,
        String comments,
        OffsetDateTime createdAt,
        List<RouteSaleDetailResponse> details,
        List<RouteSalePaymentResponse> payments
) {
}
