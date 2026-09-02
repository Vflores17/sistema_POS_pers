package com.vflores.pos.products.api;

import com.vflores.pos.adminauthorizations.application.AdminAuthorizedOperationExecutor;
import com.vflores.pos.auth.application.JwtService;
import com.vflores.pos.auth.infrastructure.security.CustomUserDetailsService;
import com.vflores.pos.auth.infrastructure.security.JwtAuthenticationFilter;
import com.vflores.pos.auth.infrastructure.security.RestAccessDeniedHandler;
import com.vflores.pos.auth.infrastructure.security.RestAuthenticationEntryPoint;
import com.vflores.pos.products.application.ProductPriceService;
import com.vflores.pos.products.application.ProductService;
import com.vflores.pos.shared.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class,
        RestAccessDeniedHandler.class, RestAuthenticationEntryPoint.class})
class ProductPriceAuthorizationTest {

    private static final UUID PRODUCT_ID = UUID.fromString("d31c9826-fac7-4d25-b741-c405bc2da571");
    private static final UUID PRICE_ID = UUID.fromString("74ed1ee0-a728-4b99-9185-b22ec08561fc");
    private static final String PRICE_BODY = """
            {"type":"DETAIL","price":1250}
            """;

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private ProductService productService;
    @MockBean
    private ProductPriceService productPriceService;
    @MockBean
    private AdminAuthorizedOperationExecutor adminAuthorizedOperationExecutor;
    @MockBean
    private CustomUserDetailsService customUserDetailsService;
    @MockBean
    private JwtService jwtService;

    @Test
    @WithMockUser(authorities = "PRICE_READ")
    void priceReadAllowsBothPriceQueries() throws Exception {
        when(productPriceService.findByProductId(PRODUCT_ID)).thenReturn(List.of());
        when(productPriceService.findAllGroupedByProduct()).thenReturn(Map.of());

        mockMvc.perform(get("/api/v1/products/{id}/prices", PRODUCT_ID)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/products/prices/all")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "PRICE_CREATE")
    void priceCreateAllowsPost() throws Exception {
        mockMvc.perform(post("/api/v1/products/{id}/prices", PRODUCT_ID).with(csrf())
                        .contentType("application/json").content(PRICE_BODY))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = "PRICE_UPDATE")
    void priceUpdateAllowsPut() throws Exception {
        mockMvc.perform(put("/api/v1/products/{id}/prices/{priceId}", PRODUCT_ID, PRICE_ID).with(csrf())
                        .contentType("application/json").content(PRICE_BODY))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "PRICE_DELETE")
    void priceDeleteAllowsDelete() throws Exception {
        mockMvc.perform(delete("/api/v1/products/{id}/prices/{priceId}", PRODUCT_ID, PRICE_ID).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser
    void authenticatedUserWithoutPricePermissionsReceivesForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/products/{id}/prices", PRODUCT_ID)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/products/{id}/prices", PRODUCT_ID).with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/products/{id}/prices/{priceId}", PRODUCT_ID, PRICE_ID).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/products/{id}/prices/{priceId}", PRODUCT_ID, PRICE_ID).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "PRODUCT_READ")
    void productReadDoesNotGrantAccessToAllPricesQuery() throws Exception {
        mockMvc.perform(get("/api/v1/products/prices/all")).andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedUserReceivesUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/products/{id}/prices", PRODUCT_ID)).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = {"ROLE_ADMIN", "PRICE_READ", "PRICE_CREATE", "PRICE_UPDATE", "PRICE_DELETE"})
    void adminEffectivePermissionsAllowEveryPriceOperation() throws Exception {
        when(productPriceService.findByProductId(PRODUCT_ID)).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/products/{id}/prices", PRODUCT_ID)).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/products/{id}/prices", PRODUCT_ID).with(csrf())
                .contentType("application/json").content(PRICE_BODY)).andExpect(status().isCreated());
        mockMvc.perform(put("/api/v1/products/{id}/prices/{priceId}", PRODUCT_ID, PRICE_ID).with(csrf())
                .contentType("application/json").content(PRICE_BODY)).andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/products/{id}/prices/{priceId}", PRODUCT_ID, PRICE_ID).with(csrf()))
                .andExpect(status().isNoContent());
    }
}
