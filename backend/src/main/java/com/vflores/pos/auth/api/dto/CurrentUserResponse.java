package com.vflores.pos.auth.api.dto;

import com.vflores.pos.users.domain.model.UserStatus;

import java.util.Set;
import java.util.UUID;

public record CurrentUserResponse(
        UUID id,
        String username,
        String email,
        String fullName,
        UserStatus status,
        Set<String> roles,
        Set<String> permissions
) {
}
