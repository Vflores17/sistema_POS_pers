package com.vflores.pos.routesales.api;

import com.vflores.pos.adminauthorizations.application.AdminAuthorizedOperationExecutor;
import com.vflores.pos.auth.application.JwtService;
import com.vflores.pos.auth.infrastructure.security.CustomUserDetailsService;
import com.vflores.pos.auth.infrastructure.security.JwtAuthenticationFilter;
import com.vflores.pos.auth.infrastructure.security.RestAccessDeniedHandler;
import com.vflores.pos.auth.infrastructure.security.RestAuthenticationEntryPoint;
import com.vflores.pos.routesales.api.dto.RouteSaleDetailResponse;
import com.vflores.pos.routesales.api.dto.RouteSaleResponse;
import com.vflores.pos.routesales.api.security.RouteSaleStatusAuthorization;
import com.vflores.pos.routesales.application.RouteSaleService;
import com.vflores.pos.routesales.domain.model.RouteSale;
import com.vflores.pos.sales.domain.model.Sale;
import com.vflores.pos.shared.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RouteSaleController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RouteSaleStatusAuthorization.class,
        RestAccessDeniedHandler.class, RestAuthenticationEntryPoint.class})
class RouteSalePriceRequestValidationTest {

    private static final UUID CLIENT_ID = UUID.fromString("c12c5f65-ced9-482e-a7ca-29460bf19748");
    private static final UUID DRIVER_ID = UUID.fromString("9b1e71d0-84a4-4e7b-9d2a-2f6d1b0c8f3a");
    private static final UUID PRODUCT_ID = UUID.fromString("3aac17f9-53e2-4104-830b-94a6ef05547d");
    private static final UUID USER_ID = UUID.fromString("e7a159f0-5e19-4b3e-9ce0-94a6ef05547d");

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

    private void whenCreateSucceeds() {
        when(routeSaleService.create(any())).thenReturn(new RouteSaleResponse(
                UUID.randomUUID(), 1L, null, USER_ID, CLIENT_ID, DRIVER_ID,
                Sale.PaymentMethod.CASH, new BigDecimal("10.00"), RouteSale.RouteStatus.PENDING,
                "ok", OffsetDateTime.now(),
                List.of(new RouteSaleDetailResponse(PRODUCT_ID, "Planta", BigDecimal.ONE,
                        new BigDecimal("10.00"), new BigDecimal("10.00"))),
                List.of()));
    }

    @Test
    @WithMockUser(authorities = "ROUTE_CREATE")
    void createWithPriceZeroIsRejectedWith400() throws Exception {
        mockMvc.perform(post("/api/v1/route-sales")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clientId":"c12c5f65-ced9-482e-a7ca-29460bf19748",
                                  "driverId":"9b1e71d0-84a4-4e7b-9d2a-2f6d1b0c8f3a",
                                  "paymentMethod":"CASH",
                                  "items":[{"productId":"3aac17f9-53e2-4104-830b-94a6ef05547d","quantity":1,"price":0}]
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "ROUTE_CREATE")
    void createWithNegativePriceIsRejectedWith400() throws Exception {
        mockMvc.perform(post("/api/v1/route-sales")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clientId":"c12c5f65-ced9-482e-a7ca-29460bf19748",
                                  "driverId":"9b1e71d0-84a4-4e7b-9d2a-2f6d1b0c8f3a",
                                  "paymentMethod":"CASH",
                                  "items":[{"productId":"3aac17f9-53e2-4104-830b-94a6ef05547d","quantity":1,"price":-1000}]
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "ROUTE_CREATE")
    void createWithCatalogPricePassesValidation() throws Exception {
        whenCreateSucceeds();
        mockMvc.perform(post("/api/v1/route-sales")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clientId":"c12c5f65-ced9-482e-a7ca-29460bf19748",
                                  "driverId":"9b1e71d0-84a4-4e7b-9d2a-2f6d1b0c8f3a",
                                  "paymentMethod":"CASH",
                                  "items":[{"productId":"3aac17f9-53e2-4104-830b-94a6ef05547d","quantity":1,"price":10.00}]
                                }
                                """))
                .andExpect(status().isCreated());
    }
}