package com.vflores.pos.clients.api;

import com.vflores.pos.adminauthorizations.application.AdminAuthorizedOperationExecutor;
import com.vflores.pos.auth.application.JwtService;
import com.vflores.pos.auth.infrastructure.security.CustomUserDetailsService;
import com.vflores.pos.auth.infrastructure.security.JwtAuthenticationFilter;
import com.vflores.pos.auth.infrastructure.security.RestAccessDeniedHandler;
import com.vflores.pos.auth.infrastructure.security.RestAuthenticationEntryPoint;
import com.vflores.pos.clients.application.ClientService;
import com.vflores.pos.shared.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.boot.test.mock.mockito.MockBean;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@WebMvcTest(ClientController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        RestAccessDeniedHandler.class,
        RestAuthenticationEntryPoint.class
})
class ClientAuthorizationTest {

    private static final UUID CLIENT_ID = UUID.fromString("6f981abb-4e17-4a8a-940f-6619c986103f");
    private static final String CLIENT_BODY = """
            {
              "name": "Client Test",
              "type": "DETAIL",
              "phone": "8888-8888"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ClientService clientService;

    @MockBean
    private AdminAuthorizedOperationExecutor adminAuthorizedOperationExecutor;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @MockBean
    private JwtService jwtService;

    @Test
    @WithMockUser(authorities = "CLIENT_READ")
    void clientReadAllowsGet() throws Exception {
        when(clientService.findAll(isNull(), isNull(), any())).thenReturn(Page.empty());
        mockMvc.perform(get("/api/v1/clients")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "CLIENT_CREATE")
    void clientCreateAllowsPost() throws Exception {
        mockMvc.perform(post("/api/v1/clients")
                        .with(csrf())
                        .contentType("application/json")
                        .content(CLIENT_BODY))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = "CLIENT_UPDATE")
    void clientUpdateAllowsPut() throws Exception {
        mockMvc.perform(put("/api/v1/clients/{id}", CLIENT_ID)
                        .with(csrf())
                        .contentType("application/json")
                        .content(CLIENT_BODY))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void clientUpdateWithHeaderDelegatesToTemporaryAuthorization() throws Exception {
        mockMvc.perform(put("/api/v1/clients/{id}", CLIENT_ID).with(csrf())
                        .header("X-Admin-Authorization", "plain-token")
                        .contentType("application/json").content(CLIENT_BODY))
                .andExpect(status().isOk());

        verify(adminAuthorizedOperationExecutor).execute(
                any(), org.mockito.ArgumentMatchers.eq("CLIENT_UPDATE"),
                org.mockito.ArgumentMatchers.eq("CLIENT_UPDATE"), org.mockito.ArgumentMatchers.eq("CLIENT"),
                org.mockito.ArgumentMatchers.eq(CLIENT_ID), org.mockito.ArgumentMatchers.eq("plain-token"), any()
        );
    }

    @Test
    @WithMockUser
    void missingTemporaryAuthorizationReturnsMachineReadableError() throws Exception {
        mockMvc.perform(put("/api/v1/clients/{id}", CLIENT_ID).with(csrf())
                        .contentType("application/json").content(CLIENT_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ADMIN_AUTHORIZATION_REQUIRED"));
    }

    @Test
    @WithMockUser
    void temporaryHeaderNeverOverridesDelete() throws Exception {
        mockMvc.perform(delete("/api/v1/clients/{id}", CLIENT_ID).with(csrf())
                        .header("X-Admin-Authorization", "plain-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
    }

    @Test
    @WithMockUser(authorities = "CLIENT_DELETE")
    void clientDeleteAllowsDelete() throws Exception {
        mockMvc.perform(delete("/api/v1/clients/{id}", CLIENT_ID).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser
    void authenticatedUserWithoutClientPermissionsReceivesForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/clients")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/clients").with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/clients/{id}", CLIENT_ID).with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/clients/{id}", CLIENT_ID).with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedUserReceivesUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/clients")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = {"ROLE_ADMIN", "CLIENT_READ", "CLIENT_CREATE", "CLIENT_UPDATE", "CLIENT_DELETE"})
    void adminEffectivePermissionsAllowEveryClientOperation() throws Exception {
        when(clientService.findAll(isNull(), isNull(), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/clients")).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/clients")
                        .with(csrf()).contentType("application/json").content(CLIENT_BODY))
                .andExpect(status().isCreated());
        mockMvc.perform(put("/api/v1/clients/{id}", CLIENT_ID)
                        .with(csrf()).contentType("application/json").content(CLIENT_BODY))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/clients/{id}", CLIENT_ID).with(csrf()))
                .andExpect(status().isNoContent());
    }
}
