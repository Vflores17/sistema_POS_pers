package com.vflores.pos.routesales.api;

import com.vflores.pos.adminauthorizations.application.AdminAuthorizedOperationExecutor;
import com.vflores.pos.auth.application.JwtService;
import com.vflores.pos.auth.infrastructure.security.CustomUserDetailsService;
import com.vflores.pos.auth.infrastructure.security.JwtAuthenticationFilter;
import com.vflores.pos.auth.infrastructure.security.RestAccessDeniedHandler;
import com.vflores.pos.auth.infrastructure.security.RestAuthenticationEntryPoint;
import com.vflores.pos.routesales.api.security.RouteSaleStatusAuthorization;
import com.vflores.pos.routesales.application.RouteSaleService;
import com.vflores.pos.shared.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

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

@WebMvcTest(RouteSaleController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RouteSaleStatusAuthorization.class,
        RestAccessDeniedHandler.class, RestAuthenticationEntryPoint.class})
class RouteSaleAuthorizationTest {

    private static final UUID ROUTE_ID = UUID.fromString("fc25c391-3473-437d-b4e2-44882e554ae2");
    private static final String ROUTE_BODY = """
            {
              "clientId":"c12c5f65-ced9-482e-a7ca-29460bf19748",
              "driverId":"20fcb2d8-05f6-4c71-83a7-af8ed82e9a63",
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
    private RouteSaleService routeSaleService;
    @MockBean
    private AdminAuthorizedOperationExecutor adminAuthorizedOperationExecutor;
    @MockBean
    private CustomUserDetailsService customUserDetailsService;
    @MockBean
    private JwtService jwtService;

    @Test
    @WithMockUser(authorities = "ROUTE_READ")
    void routeReadAllowsQueries() throws Exception {
        when(routeSaleService.findAll()).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/route-sales")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/route-sales/{id}", ROUTE_ID)).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "ROUTE_CREATE")
    void routeCreateAllowsCreationAndNextInvoiceNumber() throws Exception {
        when(routeSaleService.getNextInvoiceNumber()).thenReturn(1L);
        mockMvc.perform(get("/api/v1/route-sales/next-invoice-number")).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/route-sales").with(csrf())
                .contentType("application/json").content(ROUTE_BODY)).andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = "ROUTE_UPDATE")
    void routeUpdateAllowsEditPaymentsAndNonCancelledStatuses() throws Exception {
        mockMvc.perform(put("/api/v1/route-sales/{id}", ROUTE_ID).with(csrf())
                .contentType("application/json").content(ROUTE_BODY)).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/route-sales/{id}/payments", ROUTE_ID).with(csrf())
                .contentType("application/json").content(PAYMENT_BODY)).andExpect(status().isOk());
        for (String statusValue : List.of("PENDING", "PAID")) {
            mockMvc.perform(patch("/api/v1/route-sales/{id}/status", ROUTE_ID).with(csrf())
                    .contentType("application/json").content("{\"status\":\"" + statusValue + "\"}"))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @WithMockUser
    void routeUpdateWithHeaderDelegatesToTemporaryAuthorization() throws Exception {
        mockMvc.perform(put("/api/v1/route-sales/{id}", ROUTE_ID).with(csrf())
                        .header("X-Admin-Authorization", "plain-token")
                        .contentType("application/json").content(ROUTE_BODY))
                .andExpect(status().isOk());

        verify(adminAuthorizedOperationExecutor).execute(
                any(), org.mockito.ArgumentMatchers.eq("ROUTE_UPDATE"),
                org.mockito.ArgumentMatchers.eq("ROUTE_UPDATE"), org.mockito.ArgumentMatchers.eq("ROUTE"),
                org.mockito.ArgumentMatchers.eq(ROUTE_ID), org.mockito.ArgumentMatchers.eq("plain-token"), any()
        );
    }

    @Test
    @WithMockUser(authorities = "ROUTE_DELETE")
    void routeDeleteAllowsDelete() throws Exception {
        mockMvc.perform(delete("/api/v1/route-sales/{id}", ROUTE_ID).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(authorities = "ROUTE_CANCEL")
    void routeCancelAllowsCancelledStatusOnly() throws Exception {
        mockMvc.perform(patch("/api/v1/route-sales/{id}/status", ROUTE_ID).with(csrf())
                .contentType("application/json").content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/route-sales/{id}/status", ROUTE_ID).with(csrf())
                .contentType("application/json").content("{\"status\":\"PAID\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROUTE_UPDATE")
    void routeUpdateDoesNotAllowCancellation() throws Exception {
        mockMvc.perform(patch("/api/v1/route-sales/{id}/status", ROUTE_ID).with(csrf())
                .contentType("application/json").content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROUTE_READ")
    void readOnlyRouteUserCannotWrite() throws Exception {
        mockMvc.perform(post("/api/v1/route-sales").with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/route-sales/{id}", ROUTE_ID).with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/route-sales/{id}", ROUTE_ID).with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/route-sales/{id}/payments", ROUTE_ID).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/route-sales/{id}/status", ROUTE_ID).with(csrf())
                .contentType("application/json").content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void authenticatedUserWithoutRoutePermissionsReceivesForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/route-sales")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/route-sales").with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/route-sales/{id}", ROUTE_ID).with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/route-sales/{id}", ROUTE_ID).with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedUserReceivesUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/route-sales")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = {"ROLE_ADMIN", "ROUTE_READ", "ROUTE_CREATE", "ROUTE_UPDATE", "ROUTE_DELETE", "ROUTE_CANCEL"})
    void adminEffectivePermissionsAllowEveryRouteOperation() throws Exception {
        when(routeSaleService.findAll()).thenReturn(List.of());
        when(routeSaleService.getNextInvoiceNumber()).thenReturn(1L);
        mockMvc.perform(get("/api/v1/route-sales")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/route-sales/next-invoice-number")).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/route-sales").with(csrf())
                .contentType("application/json").content(ROUTE_BODY)).andExpect(status().isCreated());
        mockMvc.perform(put("/api/v1/route-sales/{id}", ROUTE_ID).with(csrf())
                .contentType("application/json").content(ROUTE_BODY)).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/route-sales/{id}/payments", ROUTE_ID).with(csrf())
                .contentType("application/json").content(PAYMENT_BODY)).andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/route-sales/{id}/status", ROUTE_ID).with(csrf())
                .contentType("application/json").content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/route-sales/{id}", ROUTE_ID).with(csrf()))
                .andExpect(status().isNoContent());
    }
}
