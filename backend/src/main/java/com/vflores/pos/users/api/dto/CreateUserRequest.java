package com.vflores.pos.users.api.dto;

import com.vflores.pos.users.domain.model.UserStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

public record CreateUserRequest(
        @NotBlank
        @Size(min = 3, max = 60)
        @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "username only allows letters, numbers and underscore")
        String username,

        @NotBlank
        @Email
        @Size(max = 120)
        String email,

        @NotBlank
        @Size(max = 150)
        String fullName,

        @NotBlank
        @Size(min = 8, max = 120)
        String password,

        @NotNull
        UserStatus status,

        @NotEmpty
        Set<UUID> roleIds
) {
}
