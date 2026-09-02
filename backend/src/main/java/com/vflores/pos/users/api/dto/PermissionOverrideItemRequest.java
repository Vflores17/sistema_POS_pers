package com.vflores.pos.users.api.dto;

import com.vflores.pos.users.domain.model.PermissionOverrideEffect;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PermissionOverrideItemRequest(
        @NotNull UUID permissionId,
        @NotNull PermissionOverrideEffect effect
) {
}
