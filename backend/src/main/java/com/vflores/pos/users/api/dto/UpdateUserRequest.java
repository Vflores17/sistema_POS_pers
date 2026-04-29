package com.vflores.pos.users.api.dto;

import com.vflores.pos.users.domain.model.UserStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

public record UpdateUserRequest(
        @NotBlank
        @Email
        @Size(max = 120)
        String email,

        @NotBlank
        @Size(max = 150)
        String fullName,

        @NotNull
        UserStatus status,

        @NotEmpty
        Set<UUID> roleIds
) {
}
