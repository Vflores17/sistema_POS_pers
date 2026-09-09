package com.vflores.pos.sales.api;

import com.vflores.pos.adminauthorizations.application.AdminAuthorizedOperationExecutor;
import com.vflores.pos.adminauthorizations.application.AdminAuthorizationRequiredException;
import com.vflores.pos.auth.application.JwtService;
import com.vflores.pos.auth.infrastructure.security.CustomUserDetailsService;
import com.vflores.pos.auth.infrastructure.security.JwtAuthenticationFilter;
import com.vflores.pos.auth.infrastructure.security.RestAccessDeniedHandler;
import com.vflores.pos.auth.infrastructure.security.RestAuthenticationEntryPoint;
import com.vflores.pos.sales.api.security.SaleStatusAuthorization;
import com.vflores.pos.sales.application.SaleService;
import com.vflores.pos.shared.config.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SaleController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, SaleStatusAuthorization.class,
        RestAccessDeniedHandler.class, RestAuthenticationEntryPoint.class})
class SalePaymentModifyAuthorizationTest {

    private static final UUID SALE_ID = UUID.fromString("28343428-e94c-4ec4-a256-f132daf743f5");
    private static final String PAYMENT_BODY = """
            [{"method":"CASH","amount":1000}]
            """;

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private SaleService saleService;
    @MockBean
    private AdminAuthorizedOperationExecutor adminAuthorizedOperationExecutor;
    @MockBean
    private CustomUserDetailsService customUserDetailsService;
    @MockBean
    private JwtService jwtService;

    @BeforeEach
    void enforceStrictAdminGate() {
        when(adminAuthorizedOperationExecutor.executeForAdminActor(
                any(), eq("SALE_PAYMENT_MODIFY"), eq("SALE"), eq(SALE_ID), any(), any()
        )).thenAnswer(invocation -> {
            Authentication authentication = invocation.getArgument(0);
            String token = invocation.getArgument(4);
            Supplier<?> operation = invocation.getArgument(5);
            boolean isAdmin = authentication.getAuthorities().stream()
                    .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
            if (isAdmin) {
                return operation.get();
            }
            if (token == null || token.isBlank()) {
                throw new AdminAuthorizationRequiredException();
            }
            return null;
        });
    }

    @Test
    @WithMockUser(authorities = {"ROLE_ADMIN", "SALE_PAYMENT_MODIFY"})
    void adminCanReplacePaymentsWithoutTemporaryAuthorization() throws Exception {
        mockMvc.perform(put("/api/v1/sales/{id}/payments", SALE_ID).with(csrf())
                        .contentType("application/json").content(PAYMENT_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(saleService).replacePayments(eq(SALE_ID), any());
    }

    @Test
    @WithMockUser(authorities = "SALE_PAYMENT_MODIFY")
    void permissionWithoutAdminRoleRequiresTemporaryAuthorization() throws Exception {
        mockMvc.perform(put("/api/v1/sales/{id}/payments", SALE_ID).with(csrf())
                        .contentType("application/json").content(PAYMENT_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ADMIN_AUTHORIZATION_REQUIRED"));

        verify(saleService, never()).replacePayments(any(), any());
    }

    @Test
    @WithMockUser(authorities = "SALE_PAYMENT_MODIFY")
    void permissionWithHeaderDelegatesToTemporaryAuthorization() throws Exception {
        mockMvc.perform(put("/api/v1/sales/{id}/payments", SALE_ID).with(csrf())
                        .header("X-Admin-Authorization", "plain-token")
                        .contentType("application/json").content(PAYMENT_BODY))
                .andExpect(status().isOk());

        verify(adminAuthorizedOperationExecutor).executeForAdminActor(
                any(), eq("SALE_PAYMENT_MODIFY"), eq("SALE"), eq(SALE_ID), eq("plain-token"), any()
        );
    }

    @Test
    @WithMockUser
    void withoutPermissionOrHeaderAccessIsForbidden() throws Exception {
        mockMvc.perform(put("/api/v1/sales/{id}/payments", SALE_ID).with(csrf())
                        .contentType("application/json").content(PAYMENT_BODY))
                .andExpect(status().isForbidden());

        verify(saleService, never()).replacePayments(any(), any());
    }

    @Test
    void unauthenticatedIsUnauthorized() throws Exception {
        mockMvc.perform(put("/api/v1/sales/{id}/payments", SALE_ID).with(csrf())
                        .contentType("application/json").content(PAYMENT_BODY))
                .andExpect(status().isUnauthorized());
    }
}