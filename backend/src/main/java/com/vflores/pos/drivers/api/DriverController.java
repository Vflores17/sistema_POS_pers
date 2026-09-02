package com.vflores.pos.drivers.api;

import com.vflores.pos.adminauthorizations.application.AdminAuthorizedOperationExecutor;
import com.vflores.pos.drivers.api.dto.CreateDriverRequest;
import com.vflores.pos.drivers.api.dto.DriverResponse;
import com.vflores.pos.drivers.api.dto.UpdateDriverRequest;
import com.vflores.pos.drivers.application.DriverService;
import com.vflores.pos.shared.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/drivers")
@RequiredArgsConstructor
public class DriverController {

    private final DriverService driverService;
    private final AdminAuthorizedOperationExecutor adminAuthorizedOperationExecutor;

    @GetMapping
    public ResponseEntity<ApiResponse<List<DriverResponse>>> findAll() {
        return ResponseEntity.ok(ApiResponse.ok(driverService.findAll()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DriverResponse>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(driverService.findById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DriverResponse>> create(@Valid @RequestBody CreateDriverRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(driverService.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<DriverResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDriverRequest request,
            @RequestHeader(value = "X-Admin-Authorization", required = false) String adminAuthorization,
            Authentication authentication
    ) {
        DriverResponse response = adminAuthorizedOperationExecutor.execute(
                authentication, "DRIVER_UPDATE", "DRIVER_UPDATE", "DRIVER", id, adminAuthorization,
                () -> driverService.update(id, request)
        );
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        driverService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
