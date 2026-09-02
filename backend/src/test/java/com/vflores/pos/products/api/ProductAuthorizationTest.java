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
import org.springframework.data.domain.Page;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class,
        RestAccessDeniedHandler.class, RestAuthenticationEntryPoint.class})
class ProductAuthorizationTest {

    private static final UUID PRODUCT_ID = UUID.fromString("d31c9826-fac7-4d25-b741-c405bc2da571");
    private static final String PRODUCT_BODY = """
            {"name":"Plant Test","price":1000,"stock":10,"status":"ACTIVE"}
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
    @WithMockUser(authorities = "PRODUCT_READ")
    void productReadAllowsGet() throws Exception {
        when(productService.findAll(isNull(), isNull(), any())).thenReturn(Page.empty());
        mockMvc.perform(get("/api/v1/products")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "PRODUCT_CREATE")
    void productCreateAllowsPost() throws Exception {
        mockMvc.perform(post("/api/v1/products").with(csrf())
                        .contentType("application/json").content(PRODUCT_BODY))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = "PRODUCT_UPDATE")
    void productUpdateAllowsPut() throws Exception {
        mockMvc.perform(put("/api/v1/products/{id}", PRODUCT_ID).with(csrf())
                        .contentType("application/json").content(PRODUCT_BODY))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void productUpdateWithHeaderDelegatesToTemporaryAuthorization() throws Exception {
        mockMvc.perform(put("/api/v1/products/{id}", PRODUCT_ID).with(csrf())
                        .header("X-Admin-Authorization", "plain-token")
                        .contentType("application/json").content(PRODUCT_BODY))
                .andExpect(status().isOk());

        verify(adminAuthorizedOperationExecutor).execute(
                any(), org.mockito.ArgumentMatchers.eq("PRODUCT_UPDATE"),
                org.mockito.ArgumentMatchers.eq("PRODUCT_UPDATE"), org.mockito.ArgumentMatchers.eq("PRODUCT"),
                org.mockito.ArgumentMatchers.eq(PRODUCT_ID), org.mockito.ArgumentMatchers.eq("plain-token"), any()
        );
    }

    @Test
    @WithMockUser(authorities = "PRODUCT_DELETE")
    void productDeleteAllowsDelete() throws Exception {
        mockMvc.perform(delete("/api/v1/products/{id}", PRODUCT_ID).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser
    void authenticatedUserWithoutProductPermissionsReceivesForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/products")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/products").with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/products/{id}", PRODUCT_ID).with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/products/{id}", PRODUCT_ID).with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedUserReceivesUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/products")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = {"ROLE_ADMIN", "PRODUCT_READ", "PRODUCT_CREATE", "PRODUCT_UPDATE", "PRODUCT_DELETE"})
    void adminEffectivePermissionsAllowEveryProductOperation() throws Exception {
        when(productService.findAll(isNull(), isNull(), any())).thenReturn(Page.empty());
        mockMvc.perform(get("/api/v1/products")).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/products").with(csrf())
                .contentType("application/json").content(PRODUCT_BODY)).andExpect(status().isCreated());
        mockMvc.perform(put("/api/v1/products/{id}", PRODUCT_ID).with(csrf())
                .contentType("application/json").content(PRODUCT_BODY)).andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/products/{id}", PRODUCT_ID).with(csrf()))
                .andExpect(status().isNoContent());
    }
}
