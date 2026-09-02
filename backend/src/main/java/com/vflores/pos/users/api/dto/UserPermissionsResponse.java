package com.vflores.pos.users.api.dto;

import java.util.Set;
import java.util.UUID;

public record UserPermissionsResponse(
        UUID userId,
        Set<String> inheritedPermissions,
        Set<String> allowedPermissions,
        Set<String> deniedPermissions,
        Set<String> effectivePermissions
) {
}
