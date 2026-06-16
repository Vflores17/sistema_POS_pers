package com.vflores.pos.products.domain.repository;

import com.vflores.pos.products.domain.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {

    List<Product> findByNameIgnoreCase(String name);
    @Query("SELECT COUNT(sd) FROM SaleDetail sd WHERE sd.product.id = :productId")
    long countSaleDetailsByProductId(@Param("productId") UUID productId);
}