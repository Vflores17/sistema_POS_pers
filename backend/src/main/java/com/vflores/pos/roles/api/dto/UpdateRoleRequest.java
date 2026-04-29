package com.vflores.pos.roles.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateRoleRequest(
        @NotBlank
        @Size(max = 255)
        String description,

        @NotNull
        Boolean active
) {
}
