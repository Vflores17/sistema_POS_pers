package com.vflores.pos.products.application;

import com.vflores.pos.products.api.dto.CreateProductRequest;
import com.vflores.pos.products.api.dto.ProductResponse;
import com.vflores.pos.products.api.dto.UpdateProductRequest;
import com.vflores.pos.products.domain.model.Product;
import com.vflores.pos.products.domain.model.ProductStatus;
import com.vflores.pos.products.domain.repository.ProductRepository;
import com.vflores.pos.shared.exception.ConflictException;
import com.vflores.pos.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.vflores.pos.products.domain.repository.ProductPriceRepository;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductPriceRepository productPriceRepository;

    @Transactional(readOnly = true)
    public Page<ProductResponse> findAll(String search, ProductStatus status, Pageable pageable) {
        Specification<Product> specification = (root, query, cb) -> cb.conjunction();

        if (search != null && !search.isBlank()) {
            String pattern = "%" + search.trim().toLowerCase() + "%";
            specification = specification.and((root, query, cb) ->
                    cb.like(cb.lower(root.get("name")), pattern));
        }

        if (status != null) {
            specification = specification.and((root, query, cb) ->
                    cb.equal(root.get("status"), status));
        }

        return productRepository.findAll(specification, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ProductResponse findById(UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
        return toResponse(product);
    }

    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        validateUniqueName(request.name(), null);

        Product product = Product.builder()
                .name(normalizeName(request.name()))
                .price(request.price())
                .stock(request.stock())
                .status(request.status())
                .build();

        return toResponse(productRepository.save(product));
    }

    @Transactional
    public ProductResponse update(UUID id, UpdateProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));

        validateUniqueName(request.name(), product.getId());

        product.setName(normalizeName(request.name()));
        product.setPrice(request.price());
        product.setStock(request.stock());
        product.setStatus(request.status());

        return toResponse(productRepository.save(product));
    }

    @Transactional
    public void delete(UUID id) {
        if (!productRepository.existsById(id)) {
            throw new ResourceNotFoundException("Product not found: " + id);
        }
        productPriceRepository.deleteByProductId(id);
        productRepository.deleteById(id);
    }

    private void validateUniqueName(String name, UUID currentId) {
        productRepository.findByNameIgnoreCase(name.trim())
                .filter(existing -> !existing.getId().equals(currentId))
                .ifPresent(existing -> {
                    throw new ConflictException("Product name is already registered");
                });
    }

    private String normalizeName(String name) {
        return name.trim();
    }

    private ProductResponse toResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getStock(),
                product.getStatus(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
