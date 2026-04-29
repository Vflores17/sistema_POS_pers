package com.vflores.pos.users.api.dto;

import com.vflores.pos.users.domain.model.UserStatus;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String username,
        String email,
        String fullName,
        UserStatus status,
        Set<RoleSummary> roles,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public record RoleSummary(
            UUID id,
            String name
    ) {
    }
}
