package com.vflores.pos.products.api.dto;

import com.vflores.pos.products.domain.model.ProductPriceType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateProductPriceRequest(
        @NotNull
        ProductPriceType type,

        @NotNull
        @DecimalMin(value = "0.01", message = "price must be greater than 0")
        BigDecimal price
) {
}
