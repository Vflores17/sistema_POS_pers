package com.vflores.pos.users.api;

import com.vflores.pos.shared.response.ApiResponse;
import com.vflores.pos.shared.response.PageMeta;
import com.vflores.pos.users.api.dto.AssignRolesRequest;
import com.vflores.pos.users.api.dto.CreateUserRequest;
import com.vflores.pos.users.api.dto.UpdateUserRequest;
import com.vflores.pos.users.api.dto.UserResponse;
import com.vflores.pos.users.application.UserService;
import com.vflores.pos.users.domain.model.UserStatus;
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
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private static final List<String> ALLOWED_SORT_FIELDS = List.of("username", "email", "fullName", "createdAt", "updatedAt");

    private final UserService userService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<UserResponse>>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UserStatus status
    ) {
        Pageable pageable = toPageable(page, size, sort);
        Page<UserResponse> usersPage = userService.findAll(search, status, pageable);

        PageMeta pageMeta = new PageMeta(
                usersPage.getNumber(),
                usersPage.getSize(),
                usersPage.getTotalElements(),
                usersPage.getTotalPages(),
                usersPage.hasNext(),
                usersPage.hasPrevious()
        );
        return ResponseEntity.ok(ApiResponse.ok(usersPage.getContent(), pageMeta));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(userService.findById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UserResponse>> create(@Valid @RequestBody CreateUserRequest request) {
        UserResponse created = userService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(userService.update(id, request)));
    }

    @PatchMapping("/{id}/roles")
    public ResponseEntity<ApiResponse<UserResponse>> assignRoles(
            @PathVariable UUID id,
            @Valid @RequestBody AssignRolesRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(userService.assignRoles(id, request.roleIds())));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private Pageable toPageable(int page, int size, String sortParam) {
        int safePage = Math.max(page, 0);
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
