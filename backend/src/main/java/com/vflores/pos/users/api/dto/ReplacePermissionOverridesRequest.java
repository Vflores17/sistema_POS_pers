package com.vflores.pos.users.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ReplacePermissionOverridesRequest(
        @NotNull List<@Valid PermissionOverrideItemRequest> overrides
) {
}
