package com.vflores.pos.drivers.api.dto;

import com.vflores.pos.drivers.domain.model.Driver;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateDriverRequest(
        @NotBlank
        @Size(max = 100)
        String name,

        @NotNull
        Driver.DriverStatus status
) {
}
