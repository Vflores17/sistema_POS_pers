package com.vflores.pos.roles.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateRoleRequest(
        @NotBlank
        @Size(max = 60)
        String name,

        @Size(max = 255)
        String description,

        @NotNull
        Boolean active
) {
}
