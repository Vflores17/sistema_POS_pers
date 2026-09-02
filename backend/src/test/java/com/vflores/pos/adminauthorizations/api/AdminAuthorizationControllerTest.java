package com.vflores.pos.adminauthorizations.api;

import com.vflores.pos.adminauthorizations.api.dto.AdminAuthorizationResponse;
import com.vflores.pos.adminauthorizations.application.AdminAuthorizationService;
import com.vflores.pos.auth.application.JwtService;
import com.vflores.pos.auth.infrastructure.security.AuthenticatedUser;
import com.vflores.pos.auth.infrastructure.security.CustomUserDetailsService;
import com.vflores.pos.auth.infrastructure.security.JwtAuthenticationFilter;
import com.vflores.pos.auth.infrastructure.security.RestAccessDeniedHandler;
import com.vflores.pos.auth.infrastructure.security.RestAuthenticationEntryPoint;
import com.vflores.pos.shared.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminAuthorizationController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class,
        RestAccessDeniedHandler.class, RestAuthenticationEntryPoint.class})
class AdminAuthorizationControllerTest {

    private static final UUID REQUESTER_ID = UUID.fromString("3837f32e-fd1c-473f-9064-6f7364372a11");
    private static final UUID RESOURCE_ID = UUID.fromString("87f68128-37f8-4974-8dc3-f753d84f2ec3");

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private AdminAuthorizationService adminAuthorizationService;
    @MockBean
    private CustomUserDetailsService customUserDetailsService;
    @MockBean
    private JwtService jwtService;

    @Test
    void authenticatedRequesterCanRequestAuthorization() throws Exception {
        AuthenticatedUser requester = new AuthenticatedUser(
                REQUESTER_ID, "cashier", "hash", "cashier@example.com", "Cashier",
                true, false, Set.of()
        );
        AdminAuthorizationResponse response = new AdminAuthorizationResponse(
                UUID.randomUUID(), "plain-token-once", "SALE_UPDATE", "SALE", RESOURCE_ID,
                OffsetDateTime.now().plusSeconds(90)
        );
        when(adminAuthorizationService.issue(eq(REQUESTER_ID), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/admin-authorizations")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(requester, null, Set.of())))
                        .contentType("application/json")
                        .content(body()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").value("plain-token-once"));
    }

    @Test
    void unauthenticatedRequesterIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/admin-authorizations")
                        .with(csrf())
                        .contentType("application/json")
                        .content(body()))
                .andExpect(status().isUnauthorized());
    }

    private String body() {
        return """
                {
                  "adminUsername": "admin",
                  "adminPassword": "secret-password",
                  "operationKey": "SALE_UPDATE",
                  "resourceType": "SALE",
                  "resourceId": "%s"
                }
                """.formatted(RESOURCE_ID);
    }
}
