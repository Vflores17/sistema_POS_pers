package com.vflores.pos.sales.api.security;

import com.vflores.pos.sales.api.dto.UpdateSaleStatusRequest;
import com.vflores.pos.sales.domain.model.Sale.SaleStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class SaleStatusAuthorization {

    public boolean canChangeStatus(Authentication authentication, UpdateSaleStatusRequest request) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        String requiredAuthority = request != null && request.status() == SaleStatus.CANCELLED
                ? "SALE_CANCEL"
                : "SALE_UPDATE";
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(requiredAuthority));
    }
}
