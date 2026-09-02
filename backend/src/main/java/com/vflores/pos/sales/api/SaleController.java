package com.vflores.pos.sales.api;

import com.vflores.pos.adminauthorizations.application.AdminAuthorizedOperationExecutor;
import com.vflores.pos.sales.api.dto.CreateSaleRequest;
import com.vflores.pos.sales.api.dto.CreateSalePaymentRequest;
import com.vflores.pos.sales.api.dto.SaleResponse;
import com.vflores.pos.sales.api.dto.UpdateSaleRequest;
import com.vflores.pos.sales.api.dto.UpdateSaleStatusRequest;
import com.vflores.pos.sales.application.SaleService;
import com.vflores.pos.sales.api.security.SaleStatusAuthorization;
import com.vflores.pos.shared.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
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
import org.springframework.web.bind.annotation.PatchMapping;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sales")
@RequiredArgsConstructor
public class SaleController {

    private final SaleService saleService;
    private final SaleStatusAuthorization saleStatusAuthorization;
    private final AdminAuthorizedOperationExecutor adminAuthorizedOperationExecutor;

    @GetMapping
    public ResponseEntity<ApiResponse<List<SaleResponse>>> findAll() {
        return ResponseEntity.ok(ApiResponse.ok(saleService.findAll()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SaleResponse>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(saleService.findById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SaleResponse>> create(@Valid @RequestBody CreateSaleRequest request) {
        SaleResponse created = saleService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<SaleResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSaleRequest request,
            @RequestHeader(value = "X-Admin-Authorization", required = false) String adminAuthorization,
            Authentication authentication
    ) {
        SaleResponse response = adminAuthorizedOperationExecutor.execute(
                authentication, "SALE_UPDATE", "SALE_UPDATE", "SALE", id, adminAuthorization,
                () -> saleService.update(id, request)
        );
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        saleService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<SaleResponse> updateStatus(
            @PathVariable UUID id,
            @RequestBody UpdateSaleStatusRequest request,
            @RequestHeader(value = "X-Admin-Authorization", required = false) String adminAuthorization,
            Authentication authentication
    ) {
        if (request.status() == com.vflores.pos.sales.domain.model.Sale.SaleStatus.CANCELLED) {
            SaleResponse response = adminAuthorizedOperationExecutor.execute(
                    authentication, "SALE_CANCEL", "SALE_CANCEL", "SALE", id, adminAuthorization,
                    () -> saleService.updateStatus(id, request.status())
            );
            return ResponseEntity.ok(response);
        }
        if (!saleStatusAuthorization.canChangeStatus(authentication, request)) {
            throw new AccessDeniedException("You do not have permission for this action");
        }
        return ResponseEntity.ok(
            saleService.updateStatus(id, request.status())
        );
    }

    @GetMapping("/next-invoice-number")
    public ResponseEntity<ApiResponse<Long>> getNextInvoiceNumber() {
        return ResponseEntity.ok(ApiResponse.ok(saleService.getNextInvoiceNumber()));
    }

    @PostMapping("/{id}/payments")
    public ResponseEntity<ApiResponse<SaleResponse>> savePayments(
            @PathVariable UUID id,
            @Valid @RequestBody List<@Valid CreateSalePaymentRequest> payments
    ) {
        return ResponseEntity.ok(ApiResponse.ok(saleService.savePayments(id, payments)));
    }
}
