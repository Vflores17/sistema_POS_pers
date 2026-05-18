package com.vflores.pos.drivers.api.dto;

import com.vflores.pos.drivers.domain.model.Driver;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DriverResponse(
        UUID id,
        String name,
        Driver.DriverStatus status,
        OffsetDateTime createdAt
) {
}
