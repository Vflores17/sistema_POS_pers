package com.vflores.pos.shared.application;

import com.vflores.pos.products.domain.model.Product;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

public final class InventoryAdjustmentSupport {

    private InventoryAdjustmentSupport() {
    }

    public static <T> Map<UUID, BigDecimal> aggregateQuantities(
            List<T> items,
            Function<T, UUID> productId,
            Function<T, BigDecimal> quantity) {
        Map<UUID, BigDecimal> requestedQuantities = new LinkedHashMap<>();
        for (T item : items) {
            requestedQuantities.merge(productId.apply(item), quantity.apply(item), BigDecimal::add);
        }
        return requestedQuantities;
    }

    public static void applyStockDelta(
            Map<UUID, BigDecimal> quantities,
            Map<UUID, Product> productsById,
            int multiplier) {
        for (Map.Entry<UUID, BigDecimal> entry : quantities.entrySet()) {
            Product product = productsById.get(entry.getKey());
            BigDecimal delta = entry.getValue().multiply(BigDecimal.valueOf(multiplier));
            int newStock = product.getStock() + delta.intValue();
            product.setStock(newStock);
        }
    }

    public static <T> void restoreStockFromDetails(
            List<T> details,
            Function<T, Product> product,
            Function<T, BigDecimal> quantity) {
        Map<UUID, BigDecimal> soldQuantities = new LinkedHashMap<>();
        Map<UUID, Product> productsById = new LinkedHashMap<>();

        for (T detail : details) {
            Product detailProduct = product.apply(detail);
            UUID productId = detailProduct.getId();
            soldQuantities.merge(productId, quantity.apply(detail), BigDecimal::add);
            productsById.put(productId, detailProduct);
        }

        applyStockDelta(soldQuantities, productsById, 1);
    }
}
