package com.vflores.pos.products.application;

import com.vflores.pos.products.api.dto.CreateProductPriceRequest;
import com.vflores.pos.products.api.dto.ProductPriceResponse;
import com.vflores.pos.products.api.dto.UpdateProductPriceRequest;
import com.vflores.pos.products.domain.model.Product;
import com.vflores.pos.products.domain.model.ProductPrice;
import com.vflores.pos.products.domain.repository.ProductPriceRepository;
import com.vflores.pos.products.domain.repository.ProductRepository;
import com.vflores.pos.shared.exception.ConflictException;
import com.vflores.pos.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductPriceService {

    private final ProductRepository productRepository;
    private final ProductPriceRepository productPriceRepository;

    @Transactional(readOnly = true)
    public List<ProductPriceResponse> findByProductId(UUID productId) {
        ensureProductExists(productId);
        return productPriceRepository.findByProductIdOrderByCreatedAtDesc(productId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ProductPriceResponse create(UUID productId, CreateProductPriceRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));

        if (productPriceRepository.existsByProductIdAndType(productId, request.type())) {
            throw new ConflictException("Duplicate product price type for this product");
        }

        ProductPrice productPrice = ProductPrice.builder()
                .product(product)
                .type(request.type())
                .price(request.price())
                .build();
        return toResponse(productPriceRepository.save(productPrice));
    }

    @Transactional
    public ProductPriceResponse update(UUID productId, UUID priceId, UpdateProductPriceRequest request) {
        ensureProductExists(productId);
        ProductPrice productPrice = productPriceRepository.findByIdAndProductId(priceId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product price not found: " + priceId));

        if (productPriceRepository.existsByProductIdAndTypeAndIdNot(productId, request.type(), priceId)) {
            throw new ConflictException("Duplicate product price type for this product");
        }

        productPrice.setType(request.type());
        productPrice.setPrice(request.price());
        return toResponse(productPriceRepository.save(productPrice));
    }

    @Transactional
    public void delete(UUID productId, UUID priceId) {
        ensureProductExists(productId);
        ProductPrice productPrice = productPriceRepository.findByIdAndProductId(priceId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product price not found: " + priceId));
        productPriceRepository.delete(productPrice);
    }

    private void ensureProductExists(UUID productId) {
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Product not found: " + productId);
        }
    }

    private ProductPriceResponse toResponse(ProductPrice productPrice) {
        return new ProductPriceResponse(
                productPrice.getId(),
                productPrice.getProduct().getId(),
                productPrice.getType(),
                productPrice.getPrice(),
                productPrice.getCreatedAt(),
                productPrice.getUpdatedAt()
        );
    }
}
