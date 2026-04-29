package com.vflores.pos.roles.api;

import com.vflores.pos.roles.api.dto.CreatePermissionRequest;
import com.vflores.pos.roles.api.dto.PermissionResponse;
import com.vflores.pos.roles.api.dto.UpdatePermissionRequest;
import com.vflores.pos.roles.application.PermissionService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private static final List<String> ALLOWED_SORT_FIELDS = List.of("code", "module", "createdAt", "updatedAt");

    private final PermissionService permissionService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<PermissionResponse>>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String module
    ) {
        Pageable pageable = toPageable(page, size, sort);
        Page<PermissionResponse> permissions = permissionService.findAll(search, module, pageable);
        PageMeta meta = new PageMeta(
                permissions.getNumber(),
                permissions.getSize(),
                permissions.getTotalElements(),
                permissions.getTotalPages(),
                permissions.hasNext(),
                permissions.hasPrevious()
        );
        return ResponseEntity.ok(ApiResponse.ok(permissions.getContent(), meta));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PermissionResponse>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(permissionService.findById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PermissionResponse>> create(
            @Valid @RequestBody CreatePermissionRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(permissionService.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PermissionResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePermissionRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(permissionService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        permissionService.delete(id);
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
