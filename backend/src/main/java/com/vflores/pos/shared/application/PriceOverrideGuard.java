package com.vflores.pos.shared.application;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class PriceOverrideGuard {

    public void requireApprovedPrice(
            BigDecimal linePrice,
            BigDecimal catalogPrice,
            BigDecimal storedPrice,
            String permissionCode
    ) {
        if (linePrice.compareTo(catalogPrice) == 0) {
            return;
        }
        if (storedPrice != null && linePrice.compareTo(storedPrice) == 0) {
            return;
        }
        requireOverride(permissionCode);
    }

    private void requireOverride(String permissionCode) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean granted = authentication != null
                && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .anyMatch(permissionCode::equals);
        if (!granted) {
            throw new AccessDeniedException("Price override requires permission " + permissionCode);
        }
    }
}