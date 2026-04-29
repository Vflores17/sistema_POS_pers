package com.vflores.pos.roles.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PermissionResponse(
        UUID id,
        String code,
        String module,
        String description,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
