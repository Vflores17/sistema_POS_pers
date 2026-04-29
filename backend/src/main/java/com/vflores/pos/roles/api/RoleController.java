package com.vflores.pos.roles.api;

import com.vflores.pos.roles.api.dto.AssignPermissionsRequest;
import com.vflores.pos.roles.api.dto.CreateRoleRequest;
import com.vflores.pos.roles.api.dto.RoleResponse;
import com.vflores.pos.roles.api.dto.UpdateRoleRequest;
import com.vflores.pos.roles.application.RoleService;
import com.vflores.pos.shared.response.ApiResponse;
import com.vflores.pos.shared.response.PageMeta;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
public class RoleController {

    private static final List<String> ALLOWED_SORT_FIELDS = List.of("name", "createdAt", "updatedAt");

    private final RoleService roleService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<RoleResponse>>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active
    ) {
        Pageable pageable = toPageable(page, size, sort);
        Page<RoleResponse> roles = roleService.findAll(search, active, pageable);
        PageMeta meta = new PageMeta(
                roles.getNumber(),
                roles.getSize(),
                roles.getTotalElements(),
                roles.getTotalPages(),
                roles.hasNext(),
                roles.hasPrevious()
        );
        return ResponseEntity.ok(ApiResponse.ok(roles.getContent(), meta));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RoleResponse>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(roleService.findById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RoleResponse>> create(@Valid @RequestBody CreateRoleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(roleService.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<RoleResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRoleRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(roleService.update(id, request)));
    }

    @PatchMapping("/{id}/permissions")
    public ResponseEntity<ApiResponse<RoleResponse>> assignPermissions(
            @PathVariable UUID id,
            @Valid @RequestBody AssignPermissionsRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(roleService.assignPermissions(id, request.permissionIds())));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        roleService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private Pageable toPageable(int page, int size, String sortParam) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(size, 1), 100);

        String[] sortParts = sortParam.split(",");
        String sortBy = sortParts.length > 0 ? sortParts[0] : "createdAt";
        if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
            sortBy = "createdAt";
        }

        Sort.Direction direction = sortParts.length > 1 && "asc".equalsIgnoreCase(sortParts[1])
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        return PageRequest.of(safePage, safeSize, Sort.by(direction, sortBy));
    }
}
