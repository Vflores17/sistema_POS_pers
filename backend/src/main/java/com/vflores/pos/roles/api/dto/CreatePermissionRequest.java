package com.vflores.pos.roles.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePermissionRequest(
        @NotBlank
        @Size(max = 80)
        String code,

        @NotBlank
        @Size(max = 80)
        String module,

        @Size(max = 255)
        String description
) {
}
