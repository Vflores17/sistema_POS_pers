package com.vflores.pos.clients.api.dto;

import com.vflores.pos.clients.domain.model.ClientType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateClientRequest(
        @NotBlank
        @Size(max = 150)
        String name,

        @NotNull
        ClientType type,

        @Size(max = 40)
        String phone
) {
}
