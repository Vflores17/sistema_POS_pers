package com.vflores.pos.users.api.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.Set;
import java.util.UUID;

public record AssignRolesRequest(
        @NotEmpty
        Set<UUID> roleIds
) {
}
