package com.vflores.pos.sales.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record SaleResponse(
        UUID id,
        BigDecimal total,
        UUID userId,
        OffsetDateTime createdAt,
        List<SaleDetailResponse> details
) {
}
