package com.vflores.pos.sales.api.dto;

import com.vflores.pos.sales.domain.model.Sale;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateSalePaymentRequest(
        @NotNull
        Sale.PaymentMethod method,

        @NotNull
        @DecimalMin(value = "0.01")
        BigDecimal amount
) {
}
