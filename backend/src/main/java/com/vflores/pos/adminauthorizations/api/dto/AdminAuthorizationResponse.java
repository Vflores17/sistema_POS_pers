package com.vflores.pos.adminauthorizations.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminAuthorizationResponse(
        UUID id,
        String token,
        String operationKey,
        String resourceType,
        UUID resourceId,
        OffsetDateTime expiresAt
) {
}
