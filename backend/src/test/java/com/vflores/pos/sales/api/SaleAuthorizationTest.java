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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@WebMvcTest(SaleController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, SaleStatusAuthorization.class,
        RestAccessDeniedHandler.class, RestAuthenticationEntryPoint.class})
class SaleAuthorizationTest {

    private static final UUID SALE_ID = UUID.fromString("28343428-e94c-4ec4-a256-f132daf743f5");
    private static final UUID CLIENT_ID = UUID.fromString("c12c5f65-ced9-482e-a7ca-29460bf19748");
    private static final UUID PRODUCT_ID = UUID.fromString("3aac17f9-53e2-4104-830b-94a6ef05547d");
    private static final String SALE_BODY = """
            {
              "clientId":"c12c5f65-ced9-482e-a7ca-29460bf19748",
              "paymentMethod":"CASH",
              "items":[{"productId":"3aac17f9-53e2-4104-830b-94a6ef05547d","quantity":1}]
            }
            """;
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
    void configureTemporaryAuthorizationExecutor() {
        org.mockito.Mockito.when(adminAuthorizedOperationExecutor.execute(
                any(), any(), any(), any(), any(), any(), any()
        )).thenAnswer(invocation -> {
            org.springframework.security.core.Authentication authentication = invocation.getArgument(0);
            String requiredPermission = invocation.getArgument(1);
            String token = invocation.getArgument(5);
            Supplier<?> operation = invocation.getArgument(6);
            boolean permitted = authentication.getAuthorities().stream()
                    .anyMatch(authority -> authority.getAuthority().equals(requiredPermission));
            if (permitted) {
                return operation.get();
            }
            if (token == null || token.isBlank()) {
                throw new AdminAuthorizationRequiredException();
            }
            return null;
        });
    }

    @Test
    @WithMockUser(authorities = "SALE_READ")
    void saleReadAllowsQueries() throws Exception {
        when(saleService.findAll()).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/sales")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/sales/{id}", SALE_ID)).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "SALE_CREATE")
    void saleCreateAllowsCreationAndNextInvoiceNumber() throws Exception {
        when(saleService.getNextInvoiceNumber()).thenReturn(1L);
        mockMvc.perform(get("/api/v1/sales/next-invoice-number")).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/sales").with(csrf())
                .contentType("application/json").content(SALE_BODY)).andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = "SALE_UPDATE")
    void saleUpdateAllowsEditPaymentsAndNonCancelledStatuses() throws Exception {
        mockMvc.perform(put("/api/v1/sales/{id}", SALE_ID).with(csrf())
                .contentType("application/json").content(SALE_BODY)).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/sales/{id}/payments", SALE_ID).with(csrf())
                .contentType("application/json").content(PAYMENT_BODY)).andExpect(status().isOk());
        for (String statusValue : List.of("PENDING", "PARTIAL", "PAID")) {
            mockMvc.perform(patch("/api/v1/sales/{id}/status", SALE_ID).with(csrf())
                    .contentType("application/json").content("{\"status\":\"" + statusValue + "\"}"))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @WithMockUser
    void saleUpdateWithHeaderDelegatesToTemporaryAuthorization() throws Exception {
        mockMvc.perform(put("/api/v1/sales/{id}", SALE_ID).with(csrf())
                        .header("X-Admin-Authorization", "plain-token")
                        .contentType("application/json").content(SALE_BODY))
                .andExpect(status().isOk());

        verify(adminAuthorizedOperationExecutor).execute(
                any(), org.mockito.ArgumentMatchers.eq("SALE_UPDATE"),
                org.mockito.ArgumentMatchers.eq("SALE_UPDATE"), org.mockito.ArgumentMatchers.eq("SALE"),
                org.mockito.ArgumentMatchers.eq(SALE_ID), org.mockito.ArgumentMatchers.eq("plain-token"), any()
        );
    }

    @Test
    @WithMockUser
    void saleCancellationWithHeaderDelegatesToTemporaryAuthorization() throws Exception {
        mockMvc.perform(patch("/api/v1/sales/{id}/status", SALE_ID).with(csrf())
                        .header("X-Admin-Authorization", "plain-token")
                        .contentType("application/json").content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isOk());

        verify(adminAuthorizedOperationExecutor).execute(
                any(), org.mockito.ArgumentMatchers.eq("SALE_CANCEL"),
                org.mockito.ArgumentMatchers.eq("SALE_CANCEL"), org.mockito.ArgumentMatchers.eq("SALE"),
                org.mockito.ArgumentMatchers.eq(SALE_ID), org.mockito.ArgumentMatchers.eq("plain-token"), any()
        );
    }

    @Test
    @WithMockUser(authorities = "SALE_DELETE")
    void saleDeleteAllowsDelete() throws Exception {
        mockMvc.perform(delete("/api/v1/sales/{id}", SALE_ID).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(authorities = "SALE_CANCEL")
    void saleCancelAllowsCancelledStatusOnly() throws Exception {
        mockMvc.perform(patch("/api/v1/sales/{id}/status", SALE_ID).with(csrf())
                .contentType("application/json").content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/sales/{id}/status", SALE_ID).with(csrf())
                .contentType("application/json").content("{\"status\":\"PAID\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "SALE_UPDATE")
    void saleUpdateDoesNotAllowCancellation() throws Exception {
        mockMvc.perform(patch("/api/v1/sales/{id}/status", SALE_ID).with(csrf())
                .contentType("application/json").content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "SALE_READ")
    void readOnlyUserCannotWrite() throws Exception {
        mockMvc.perform(post("/api/v1/sales").with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/sales/{id}", SALE_ID).with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/sales/{id}", SALE_ID).with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/sales/{id}/payments", SALE_ID).with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/sales/{id}/status", SALE_ID).with(csrf())
                .contentType("application/json").content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void authenticatedUserWithoutSalePermissionsReceivesForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/sales")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/sales").with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/sales/{id}", SALE_ID).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ADMIN_AUTHORIZATION_REQUIRED"));
        mockMvc.perform(delete("/api/v1/sales/{id}", SALE_ID).with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedUserReceivesUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/sales")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = {"ROLE_ADMIN", "SALE_READ", "SALE_CREATE", "SALE_UPDATE", "SALE_DELETE", "SALE_CANCEL"})
    void adminEffectivePermissionsAllowEverySaleOperation() throws Exception {
        when(saleService.findAll()).thenReturn(List.of());
        when(saleService.getNextInvoiceNumber()).thenReturn(1L);
        mockMvc.perform(get("/api/v1/sales")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/sales/next-invoice-number")).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/sales").with(csrf())
                .contentType("application/json").content(SALE_BODY)).andExpect(status().isCreated());
        mockMvc.perform(put("/api/v1/sales/{id}", SALE_ID).with(csrf())
                .contentType("application/json").content(SALE_BODY)).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/sales/{id}/payments", SALE_ID).with(csrf())
                .contentType("application/json").content(PAYMENT_BODY)).andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/sales/{id}/status", SALE_ID).with(csrf())
                .contentType("application/json").content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/sales/{id}", SALE_ID).with(csrf()))
                .andExpect(status().isNoContent());
    }
}
