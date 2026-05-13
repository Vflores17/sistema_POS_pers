package com.vflores.pos.routesales.api.dto;

import com.vflores.pos.routesales.domain.model.RouteSale;

public record UpdateRouteSaleStatusRequest(
        RouteSale.RouteStatus status
) {
}
