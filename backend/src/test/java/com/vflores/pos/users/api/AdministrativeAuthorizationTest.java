package com.vflores.pos.users.api;

import com.vflores.pos.auth.application.JwtService;
import com.vflores.pos.auth.infrastructure.security.CustomUserDetailsService;
import com.vflores.pos.auth.infrastructure.security.JwtAuthenticationFilter;
import com.vflores.pos.auth.infrastructure.security.RestAccessDeniedHandler;
import com.vflores.pos.auth.infrastructure.security.RestAuthenticationEntryPoint;
import com.vflores.pos.roles.api.PermissionController;
import com.vflores.pos.roles.api.RoleController;
import com.vflores.pos.roles.application.PermissionService;
import com.vflores.pos.roles.application.RoleService;
import com.vflores.pos.shared.config.SecurityConfig;
import com.vflores.pos.users.application.UserPermissionService;
import com.vflores.pos.users.application.UserService;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({UserController.class, RoleController.class, PermissionController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class,
        RestAccessDeniedHandler.class, RestAuthenticationEntryPoint.class})
class AdministrativeAuthorizationTest {

    private static final UUID ID = UUID.fromString("c1cb01db-60ad-489a-a732-ed9ceeb71ce1");
    private static final UUID ROLE_ID = UUID.fromString("3ecaa240-9e23-42a1-a6f6-fdb34b1d7bd0");
    private static final UUID PERMISSION_ID = UUID.fromString("d266d221-ad25-45f0-8a62-4fa41fd4c711");
    private static final String CREATE_USER_BODY = """
            {"username":"operator","email":"operator@example.com","fullName":"Operator",
             "password":"password123","status":"ACTIVE","roleIds":["3ecaa240-9e23-42a1-a6f6-fdb34b1d7bd0"]}
            """;
    private static final String UPDATE_USER_BODY = """
            {"email":"operator@example.com","fullName":"Operator","status":"ACTIVE",
             "roleIds":["3ecaa240-9e23-42a1-a6f6-fdb34b1d7bd0"]}
            """;
    private static final String ROLE_BODY = """
            {"name":"SUPERVISOR","description":"Supervisor","active":true}
            """;
    private static final String UPDATE_ROLE_BODY = """
            {"description":"Updated supervisor","active":true}
            """;
    private static final String PERMISSION_BODY = """
            {"code":"REPORT_READ","module":"REPORTS","description":"View reports"}
            """;
    private static final String UPDATE_PERMISSION_BODY = """
            {"module":"REPORTS","description":"Updated reports"}
            """;

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private UserService userService;
    @MockBean
    private UserPermissionService userPermissionService;
    @MockBean
    private RoleService roleService;
    @MockBean
    private PermissionService permissionService;
    @MockBean
    private CustomUserDetailsService customUserDetailsService;
    @MockBean
    private JwtService jwtService;

    @Test
    @WithMockUser(authorities = "USER_READ")
    void userReadAllowsUserAndPermissionQueriesButNotWrites() throws Exception {
        when(userService.findAll(isNull(), isNull(), any())).thenReturn(Page.empty());
        mockMvc.perform(get("/api/v1/users")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/users/{id}", ID)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/users/{id}/permissions", ID)).andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/users/{id}", ID).with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {"USER_CREATE", "USER_ASSIGN_ROLE"})
    void userCreateAndRoleAssignmentTogetherAllowCreation() throws Exception {
        mockMvc.perform(post("/api/v1/users").with(csrf())
                .contentType("application/json").content(CREATE_USER_BODY)).andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = "USER_CREATE")
    void userCreateWithoutRoleAssignmentIsForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/users").with(csrf())
                .contentType("application/json").content(CREATE_USER_BODY)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {"USER_UPDATE", "USER_ASSIGN_ROLE"})
    void userUpdateAndRoleAssignmentTogetherAllowUpdate() throws Exception {
        mockMvc.perform(put("/api/v1/users/{id}", ID).with(csrf())
                .contentType("application/json").content(UPDATE_USER_BODY)).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "USER_UPDATE")
    void userUpdateWithoutRoleAssignmentIsForbidden() throws Exception {
        mockMvc.perform(put("/api/v1/users/{id}", ID).with(csrf())
                .contentType("application/json").content(UPDATE_USER_BODY)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "USER_ASSIGN_ROLE")
    void userAssignRoleAllowsDedicatedEndpointOnly() throws Exception {
        mockMvc.perform(patch("/api/v1/users/{id}/roles", ID).with(csrf())
                .contentType("application/json").content("{\"roleIds\":[\"" + ROLE_ID + "\"]}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/users/{id}/permission-overrides", ID).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "USER_ASSIGN_PERMISSION")
    void userAssignPermissionAllowsReplacingAndClearingOverrides() throws Exception {
        String body = "{\"overrides\":[{\"permissionId\":\"" + PERMISSION_ID + "\",\"effect\":\"ALLOW\"}]}";
        mockMvc.perform(put("/api/v1/users/{id}/permission-overrides", ID).with(csrf())
                .contentType("application/json").content(body)).andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/users/{id}/permission-overrides", ID).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "USER_DELETE")
    void userDeleteAllowsDelete() throws Exception {
        mockMvc.perform(delete("/api/v1/users/{id}", ID).with(csrf())).andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(authorities = "ROLE_READ")
    void roleReadAllowsQueriesButNotWrites() throws Exception {
        when(roleService.findAll(isNull(), isNull(), any())).thenReturn(Page.empty());
        mockMvc.perform(get("/api/v1/roles")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/roles/{id}", ROLE_ID)).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/roles").with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_CREATE")
    void roleCreateAllowsPost() throws Exception {
        mockMvc.perform(post("/api/v1/roles").with(csrf())
                .contentType("application/json").content(ROLE_BODY)).andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = "ROLE_UPDATE")
    void roleUpdateAllowsPutButNotPermissionAssignment() throws Exception {
        mockMvc.perform(put("/api/v1/roles/{id}", ROLE_ID).with(csrf())
                .contentType("application/json").content(UPDATE_ROLE_BODY)).andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/roles/{id}/permissions", ROLE_ID).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_ASSIGN_PERMISSION")
    void roleAssignPermissionAllowsDedicatedEndpoint() throws Exception {
        mockMvc.perform(patch("/api/v1/roles/{id}/permissions", ROLE_ID).with(csrf())
                .contentType("application/json")
                .content("{\"permissionIds\":[\"" + PERMISSION_ID + "\"]}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "ROLE_DELETE")
    void roleDeleteAllowsDelete() throws Exception {
        mockMvc.perform(delete("/api/v1/roles/{id}", ROLE_ID).with(csrf())).andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(authorities = "PERMISSION_READ")
    void permissionReadAllowsQueriesButNotWrites() throws Exception {
        when(permissionService.findAll(isNull(), isNull(), any())).thenReturn(Page.empty());
        mockMvc.perform(get("/api/v1/permissions")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/permissions/{id}", PERMISSION_ID)).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/permissions").with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "PERMISSION_CREATE")
    void permissionCreateAllowsPost() throws Exception {
        mockMvc.perform(post("/api/v1/permissions").with(csrf())
                .contentType("application/json").content(PERMISSION_BODY)).andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = "PERMISSION_UPDATE")
    void permissionUpdateAllowsPut() throws Exception {
        mockMvc.perform(put("/api/v1/permissions/{id}", PERMISSION_ID).with(csrf())
                .contentType("application/json").content(UPDATE_PERMISSION_BODY)).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "PERMISSION_DELETE")
    void permissionDeleteAllowsDelete() throws Exception {
        mockMvc.perform(delete("/api/v1/permissions/{id}", PERMISSION_ID).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser
    void authenticatedUserWithoutAdministrativePermissionsReceivesForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/users")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/roles")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/permissions")).andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedUserReceivesUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/users")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/roles")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/permissions")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void undeclaredEndpointIsDeniedByDefault() throws Exception {
        mockMvc.perform(get("/api/v1/undeclared-endpoint"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {"ROLE_ADMIN", "USER_READ", "USER_CREATE", "USER_UPDATE", "USER_DELETE",
            "USER_ASSIGN_ROLE", "USER_ASSIGN_PERMISSION", "ROLE_READ", "ROLE_CREATE", "ROLE_UPDATE",
            "ROLE_DELETE", "ROLE_ASSIGN_PERMISSION", "PERMISSION_READ", "PERMISSION_CREATE",
            "PERMISSION_UPDATE", "PERMISSION_DELETE"})
    void adminEffectivePermissionsAllowAdministrativeOperations() throws Exception {
        when(userService.findAll(isNull(), isNull(), any())).thenReturn(Page.empty());
        when(roleService.findAll(isNull(), isNull(), any())).thenReturn(Page.empty());
        when(permissionService.findAll(isNull(), isNull(), any())).thenReturn(Page.empty());
        mockMvc.perform(get("/api/v1/users")).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/users").with(csrf())
                .contentType("application/json").content(CREATE_USER_BODY)).andExpect(status().isCreated());
        mockMvc.perform(put("/api/v1/users/{id}/permission-overrides", ID).with(csrf())
                .contentType("application/json").content("{\"overrides\":[]}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/roles").with(csrf())
                .contentType("application/json").content(ROLE_BODY)).andExpect(status().isCreated());
        mockMvc.perform(patch("/api/v1/roles/{id}/permissions", ROLE_ID).with(csrf())
                .contentType("application/json")
                .content("{\"permissionIds\":[\"" + PERMISSION_ID + "\"]}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/permissions").with(csrf())
                .contentType("application/json").content(PERMISSION_BODY)).andExpect(status().isCreated());
    }
}
