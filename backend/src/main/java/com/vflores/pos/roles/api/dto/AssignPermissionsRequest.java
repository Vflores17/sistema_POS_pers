package com.vflores.pos.roles.api.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.Set;
import java.util.UUID;

public record AssignPermissionsRequest(
        @NotEmpty
        Set<UUID> permissionIds
) {
}
