package com.vflores.pos.sales.api;

import com.vflores.pos.adminauthorizations.application.AdminAuthorizedOperationExecutor;
import com.vflores.pos.auth.application.JwtService;
import com.vflores.pos.auth.infrastructure.security.CustomUserDetailsService;
import com.vflores.pos.auth.infrastructure.security.JwtAuthenticationFilter;
import com.vflores.pos.auth.infrastructure.security.RestAccessDeniedHandler;
import com.vflores.pos.auth.infrastructure.security.RestAuthenticationEntryPoint;
import com.vflores.pos.sales.api.dto.SaleDetailResponse;
import com.vflores.pos.sales.api.dto.SaleResponse;
import com.vflores.pos.sales.api.security.SaleStatusAuthorization;
import com.vflores.pos.sales.application.SaleService;
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

@WebMvcTest(SaleController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, SaleStatusAuthorization.class,
        RestAccessDeniedHandler.class, RestAuthenticationEntryPoint.class})
class SalePriceRequestValidationTest {

    private static final UUID CLIENT_ID = UUID.fromString("c12c5f65-ced9-482e-a7ca-29460bf19748");
    private static final UUID PRODUCT_ID = UUID.fromString("3aac17f9-53e2-4104-830b-94a6ef05547d");
    private static final UUID USER_ID = UUID.fromString("e7a159f0-5e19-4b3e-9ce0-94a6ef05547d");

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

    private void whenCreateSucceeds() {
        when(saleService.create(any())).thenReturn(new SaleResponse(
                UUID.randomUUID(), 1L, new BigDecimal("10.00"), USER_ID, CLIENT_ID,
                Sale.PaymentMethod.CASH, Sale.SaleStatus.PENDING, OffsetDateTime.now(),
                List.of(new SaleDetailResponse(PRODUCT_ID, "Planta", BigDecimal.ONE,
                        new BigDecimal("10.00"), new BigDecimal("10.00"))),
                List.of(), "ok"));
    }

    @Test
    @WithMockUser(authorities = "SALE_CREATE")
    void createWithPriceZeroIsRejectedWith400() throws Exception {
        mockMvc.perform(post("/api/v1/sales")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clientId":"c12c5f65-ced9-482e-a7ca-29460bf19748",
                                  "paymentMethod":"CASH",
                                  "items":[{"productId":"3aac17f9-53e2-4104-830b-94a6ef05547d","quantity":1,"price":0}]
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "SALE_CREATE")
    void createWithNegativePriceIsRejectedWith400() throws Exception {
        mockMvc.perform(post("/api/v1/sales")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clientId":"c12c5f65-ced9-482e-a7ca-29460bf19748",
                                  "paymentMethod":"CASH",
                                  "items":[{"productId":"3aac17f9-53e2-4104-830b-94a6ef05547d","quantity":1,"price":-1000}]
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "SALE_CREATE")
    void createWithCatalogPricePassesValidation() throws Exception {
        whenCreateSucceeds();
        mockMvc.perform(post("/api/v1/sales")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clientId":"c12c5f65-ced9-482e-a7ca-29460bf19748",
                                  "paymentMethod":"CASH",
                                  "items":[{"productId":"3aac17f9-53e2-4104-830b-94a6ef05547d","quantity":1,"price":10.00}]
                                }
                                """))
                .andExpect(status().isCreated());
    }
}