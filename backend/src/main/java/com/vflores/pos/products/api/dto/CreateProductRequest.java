package com.vflores.pos.products.api.dto;

import com.vflores.pos.products.domain.model.ProductStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateProductRequest(
        @NotBlank
        @Size(max = 120)
        String name,

        @NotNull
        @DecimalMin(value = "0.01", message = "price must be greater than 0")
        BigDecimal price,

        @NotNull
        @Min(value = 0, message = "stock must be >= 0")
        Integer stock,

        @NotNull
        ProductStatus status
) {
}
