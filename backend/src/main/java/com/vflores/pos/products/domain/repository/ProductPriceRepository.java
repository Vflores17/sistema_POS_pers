package com.vflores.pos.products.domain.repository;

import com.vflores.pos.products.domain.model.ProductPrice;
import com.vflores.pos.products.domain.model.ProductPriceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductPriceRepository extends JpaRepository<ProductPrice, UUID> {

    List<ProductPrice> findByProductIdOrderByCreatedAtDesc(UUID productId);

    Optional<ProductPrice> findByIdAndProductId(UUID id, UUID productId);

    boolean existsByProductIdAndType(UUID productId, ProductPriceType type);

    boolean existsByProductIdAndTypeAndIdNot(UUID productId, ProductPriceType type, UUID id);

    Optional<ProductPrice> findByProductIdAndType(UUID productId, ProductPriceType type);
}
