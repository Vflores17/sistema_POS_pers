package com.vflores.pos.products.api;

import com.vflores.pos.products.api.dto.CreateProductPriceRequest;
import com.vflores.pos.products.api.dto.ProductPriceResponse;
import com.vflores.pos.products.api.dto.CreateProductRequest;
import com.vflores.pos.products.api.dto.ProductResponse;
import com.vflores.pos.products.api.dto.UpdateProductPriceRequest;
import com.vflores.pos.products.api.dto.UpdateProductRequest;
import com.vflores.pos.products.application.ProductPriceService;
import com.vflores.pos.products.application.ProductService;
import com.vflores.pos.products.domain.model.ProductStatus;
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
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private static final List<String> ALLOWED_SORT_FIELDS = List.of("name", "price", "stock", "createdAt", "updatedAt");

    private final ProductService productService;
    private final ProductPriceService productPriceService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProductResponse>>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ProductStatus status
    ) {
        Pageable pageable = toPageable(page, size, sort);
        Page<ProductResponse> productsPage = productService.findAll(search, status, pageable);

        PageMeta pageMeta = new PageMeta(
                productsPage.getNumber(),
                productsPage.getSize(),
                productsPage.getTotalElements(),
                productsPage.getTotalPages(),
                productsPage.hasNext(),
                productsPage.hasPrevious()
        );
        return ResponseEntity.ok(ApiResponse.ok(productsPage.getContent(), pageMeta));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(productService.findById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProductResponse>> create(@Valid @RequestBody CreateProductRequest request) {
        ProductResponse created = productService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProductRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(productService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/prices")
    public ResponseEntity<ApiResponse<List<ProductPriceResponse>>> findPrices(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(productPriceService.findByProductId(id)));
    }

    @PostMapping("/{id}/prices")
    public ResponseEntity<ApiResponse<ProductPriceResponse>> createPrice(
            @PathVariable UUID id,
            @Valid @RequestBody CreateProductPriceRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(productPriceService.create(id, request)));
    }

    @PutMapping("/{id}/prices/{priceId}")
    public ResponseEntity<ApiResponse<ProductPriceResponse>> updatePrice(
            @PathVariable UUID id,
            @PathVariable UUID priceId,
            @Valid @RequestBody UpdateProductPriceRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(productPriceService.update(id, priceId, request)));
    }

    @DeleteMapping("/{id}/prices/{priceId}")
    public ResponseEntity<Void> deletePrice(
            @PathVariable UUID id,
            @PathVariable UUID priceId
    ) {
        productPriceService.delete(id, priceId);
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
