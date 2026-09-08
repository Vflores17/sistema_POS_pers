package com.vflores.pos.shared.application;

import com.vflores.pos.auth.infrastructure.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PriceOverrideGuardTest {

    private static final String SALE_PRICE_OVERRIDE = "SALE_PRICE_OVERRIDE";
    private static final BigDecimal CATALOG_PRICE = new BigDecimal("10.00");

    private final PriceOverrideGuard guard = new PriceOverrideGuard();

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(String... authorities) {
        Set<GrantedAuthority> granted = Set.<GrantedAuthority>of(
                java.util.Arrays.stream(authorities)
                        .map(SimpleGrantedAuthority::new)
                        .toArray(GrantedAuthority[]::new));
        AuthenticatedUser user = new AuthenticatedUser(
                UUID.randomUUID(), "juan", "", "", "Juan", true, false, granted);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    @Test
    void priceEqualToCatalogIgnoringScaleIsAllowedWithoutPermission() {
        authenticate("SALE_CREATE");
        assertDoesNotThrow(() -> guard.requireApprovedPrice(
                new BigDecimal("10.0"), CATALOG_PRICE, null, SALE_PRICE_OVERRIDE));
        assertDoesNotThrow(() -> guard.requireApprovedPrice(
                new BigDecimal("10.00"), CATALOG_PRICE, null, SALE_PRICE_OVERRIDE));
    }

    @Test
    void differentPriceWithoutPermissionIsDenied() {
        authenticate("SALE_CREATE");
        assertThrows(AccessDeniedException.class, () -> guard.requireApprovedPrice(
                new BigDecimal("5.00"), CATALOG_PRICE, null, SALE_PRICE_OVERRIDE));
    }

    @Test
    void differentPriceWithoutAuthenticationIsDenied() {
        assertThrows(AccessDeniedException.class, () -> guard.requireApprovedPrice(
                new BigDecimal("5.00"), CATALOG_PRICE, null, SALE_PRICE_OVERRIDE));
    }

    @Test
    void differentPriceWithOverridePermissionIsAllowed() {
        authenticate("SALE_PRICE_OVERRIDE");
        assertDoesNotThrow(() -> guard.requireApprovedPrice(
                new BigDecimal("5.00"), CATALOG_PRICE, null, SALE_PRICE_OVERRIDE));
    }

    @Test
    void storedHistoricalPriceIsAllowedWithoutPermission() {
        authenticate("SALE_CREATE");
        assertDoesNotThrow(() -> guard.requireApprovedPrice(
                new BigDecimal("9.50"), CATALOG_PRICE, new BigDecimal("9.50"), SALE_PRICE_OVERRIDE));
    }

    @Test
    void priceDifferentFromCatalogAndHistoryRequiresPermission() {
        authenticate("SALE_CREATE");
        assertThrows(AccessDeniedException.class, () -> guard.requireApprovedPrice(
                new BigDecimal("7.00"), CATALOG_PRICE, new BigDecimal("9.50"), SALE_PRICE_OVERRIDE));
    }
}