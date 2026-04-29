package com.vflores.pos.clients.api.dto;

import com.vflores.pos.clients.domain.model.ClientType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ClientResponse(
        UUID id,
        String name,
        ClientType type,
        String phone,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
