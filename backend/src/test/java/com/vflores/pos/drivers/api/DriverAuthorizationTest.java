package com.vflores.pos.drivers.api;

import com.vflores.pos.adminauthorizations.application.AdminAuthorizedOperationExecutor;
import com.vflores.pos.auth.application.JwtService;
import com.vflores.pos.auth.infrastructure.security.CustomUserDetailsService;
import com.vflores.pos.auth.infrastructure.security.JwtAuthenticationFilter;
import com.vflores.pos.auth.infrastructure.security.RestAccessDeniedHandler;
import com.vflores.pos.auth.infrastructure.security.RestAuthenticationEntryPoint;
import com.vflores.pos.drivers.application.DriverService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DriverController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class,
        RestAccessDeniedHandler.class, RestAuthenticationEntryPoint.class})
class DriverAuthorizationTest {

    private static final UUID DRIVER_ID = UUID.fromString("20fcb2d8-05f6-4c71-83a7-af8ed82e9a63");
    private static final String DRIVER_BODY = """
            {"name":"Driver Test","status":"ACTIVE"}
            """;

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private DriverService driverService;
    @MockBean
    private AdminAuthorizedOperationExecutor adminAuthorizedOperationExecutor;
    @MockBean
    private CustomUserDetailsService customUserDetailsService;
    @MockBean
    private JwtService jwtService;

    @Test
    @WithMockUser(authorities = "DRIVER_READ")
    void driverReadAllowsQueries() throws Exception {
        when(driverService.findAll()).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/drivers")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/drivers/{id}", DRIVER_ID)).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "DRIVER_CREATE")
    void driverCreateAllowsPost() throws Exception {
        mockMvc.perform(post("/api/v1/drivers").with(csrf())
                        .contentType("application/json").content(DRIVER_BODY))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = "DRIVER_UPDATE")
    void driverUpdateAllowsPut() throws Exception {
        mockMvc.perform(put("/api/v1/drivers/{id}", DRIVER_ID).with(csrf())
                        .contentType("application/json").content(DRIVER_BODY))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void driverUpdateWithHeaderDelegatesToTemporaryAuthorization() throws Exception {
        mockMvc.perform(put("/api/v1/drivers/{id}", DRIVER_ID).with(csrf())
                        .header("X-Admin-Authorization", "plain-token")
                        .contentType("application/json").content(DRIVER_BODY))
                .andExpect(status().isOk());

        verify(adminAuthorizedOperationExecutor).execute(
                any(), org.mockito.ArgumentMatchers.eq("DRIVER_UPDATE"),
                org.mockito.ArgumentMatchers.eq("DRIVER_UPDATE"), org.mockito.ArgumentMatchers.eq("DRIVER"),
                org.mockito.ArgumentMatchers.eq(DRIVER_ID), org.mockito.ArgumentMatchers.eq("plain-token"), any()
        );
    }

    @Test
    @WithMockUser(authorities = "DRIVER_DELETE")
    void driverDeleteAllowsDelete() throws Exception {
        mockMvc.perform(delete("/api/v1/drivers/{id}", DRIVER_ID).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(authorities = "DRIVER_READ")
    void readOnlyDriverUserCannotWrite() throws Exception {
        mockMvc.perform(post("/api/v1/drivers").with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/drivers/{id}", DRIVER_ID).with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/drivers/{id}", DRIVER_ID).with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void authenticatedUserWithoutDriverPermissionsReceivesForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/drivers")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/drivers").with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/drivers/{id}", DRIVER_ID).with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/drivers/{id}", DRIVER_ID).with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedUserReceivesUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/drivers")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = {"ROLE_ADMIN", "DRIVER_READ", "DRIVER_CREATE", "DRIVER_UPDATE", "DRIVER_DELETE"})
    void adminEffectivePermissionsAllowEveryDriverOperation() throws Exception {
        when(driverService.findAll()).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/drivers")).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/drivers").with(csrf())
                .contentType("application/json").content(DRIVER_BODY)).andExpect(status().isCreated());
        mockMvc.perform(put("/api/v1/drivers/{id}", DRIVER_ID).with(csrf())
                .contentType("application/json").content(DRIVER_BODY)).andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/drivers/{id}", DRIVER_ID).with(csrf()))
                .andExpect(status().isNoContent());
    }
}
