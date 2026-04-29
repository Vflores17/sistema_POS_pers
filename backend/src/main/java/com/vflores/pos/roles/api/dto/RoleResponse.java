package com.vflores.pos.roles.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RoleResponse(
        UUID id,
        String name,
        String description,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
