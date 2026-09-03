package com.vflores.pos.routesales.api.dto;

import com.vflores.pos.sales.domain.model.Sale;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateRouteSalePaymentRequest(
        UUID id,

        @NotNull
        Sale.PaymentMethod method,

        @NotNull
        @DecimalMin(value = "0.01")
        BigDecimal amount
) {
    public CreateRouteSalePaymentRequest(Sale.PaymentMethod method, BigDecimal amount) {
        this(null, method, amount);
    }
}
