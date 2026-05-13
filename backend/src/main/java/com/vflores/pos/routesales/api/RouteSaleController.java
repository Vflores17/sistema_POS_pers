package com.vflores.pos.routesales.api;

import com.vflores.pos.routesales.api.dto.CreateRouteSalePaymentRequest;
import com.vflores.pos.routesales.api.dto.CreateRouteSaleRequest;
import com.vflores.pos.routesales.api.dto.RouteSaleResponse;
import com.vflores.pos.routesales.api.dto.UpdateRouteSaleRequest;
import com.vflores.pos.routesales.api.dto.UpdateRouteSaleStatusRequest;
import com.vflores.pos.routesales.application.RouteSaleService;
import com.vflores.pos.shared.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/route-sales")
@RequiredArgsConstructor
public class RouteSaleController {

    private final RouteSaleService routeSaleService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<RouteSaleResponse>>> findAll() {
        return ResponseEntity.ok(ApiResponse.ok(routeSaleService.findAll()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RouteSaleResponse>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(routeSaleService.findById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RouteSaleResponse>> create(@Valid @RequestBody CreateRouteSaleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(routeSaleService.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<RouteSaleResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRouteSaleRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(routeSaleService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        routeSaleService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/payments")
    public ResponseEntity<ApiResponse<RouteSaleResponse>> savePayments(
            @PathVariable UUID id,
            @Valid @RequestBody List<@Valid CreateRouteSalePaymentRequest> payments
    ) {
        return ResponseEntity.ok(ApiResponse.ok(routeSaleService.savePayments(id, payments)));
    }

    @GetMapping("/next-invoice-number")
    public ResponseEntity<ApiResponse<Long>> getNextInvoiceNumber() {
        return ResponseEntity.ok(ApiResponse.ok(routeSaleService.getNextInvoiceNumber()));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<RouteSaleResponse>> updateStatus(
            @PathVariable UUID id,
            @RequestBody UpdateRouteSaleStatusRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(routeSaleService.updateStatus(id, request.status())));
    }
}
