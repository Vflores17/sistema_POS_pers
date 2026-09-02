package com.vflores.pos.routesales.api.security;

import com.vflores.pos.routesales.api.dto.UpdateRouteSaleStatusRequest;
import com.vflores.pos.routesales.domain.model.RouteSale.RouteStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class RouteSaleStatusAuthorization {

    public boolean canChangeStatus(Authentication authentication, UpdateRouteSaleStatusRequest request) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        String requiredAuthority = request != null && request.status() == RouteStatus.CANCELLED
                ? "ROUTE_CANCEL"
                : "ROUTE_UPDATE";
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(requiredAuthority));
    }
}
