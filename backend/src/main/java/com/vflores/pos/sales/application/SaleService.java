package com.vflores.pos.sales.application;

import com.vflores.pos.products.domain.model.Product;
import com.vflores.pos.products.domain.repository.ProductRepository;
import com.vflores.pos.sales.api.dto.CreateSaleRequest;
import com.vflores.pos.sales.api.dto.SaleDetailResponse;
import com.vflores.pos.sales.api.dto.SaleItemRequest;
import com.vflores.pos.sales.api.dto.SaleResponse;
import com.vflores.pos.sales.api.dto.UpdateSaleRequest;
import com.vflores.pos.sales.domain.model.Sale;
import com.vflores.pos.sales.domain.model.SaleDetail;
import com.vflores.pos.sales.domain.repository.SaleRepository;
import com.vflores.pos.shared.exception.ConflictException;
import com.vflores.pos.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.vflores.pos.clients.domain.repository.ClientRepository;
import com.vflores.pos.clients.domain.model.Client;
import com.vflores.pos.clients.domain.model.ClientType;
import com.vflores.pos.products.domain.repository.ProductPriceRepository;
import com.vflores.pos.products.domain.model.ProductPrice;
import com.vflores.pos.products.domain.model.ProductPriceType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SaleService {

    private final SaleRepository saleRepository;
    private final ProductRepository productRepository;
    private final ClientRepository clientRepository;
    private final ProductPriceRepository productPriceRepository;
    //private SaleMapper saleMapper;

    @Transactional(readOnly = true)
    public List<SaleResponse> findAll() {
        return saleRepository.findAllWithDetails().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SaleResponse findById(UUID id) {
        Sale sale = saleRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sale not found: " + id));
        return toResponse(sale);
    }

    @Transactional
    public SaleResponse create(CreateSaleRequest request) {
        System.out.println("REQUEST = " + request + "/n");
        System.out.println("CLIENT ID = " + request.clientId()+ "/n");
        System.out.println("ITEMS = " + request.items()+ "/n");
        System.out.println("ANTES computeSale"+ "/n");
        SaleComputation computation = computeSale(request.clientId(), request.items());

        System.out.println("ANTES construir sale"+ "/n");
        Sale sale = Sale.builder()
        .userId(request.userId())
        .total(computation.total())
        .status(Sale.SaleStatus.PENDING) 
        .details(new ArrayList<>())
        .build();

        System.out.println("ANTES details"+ "/n");
        sale.getDetails().addAll(buildSaleDetails(sale, computation.lines()));

        System.out.println("ANTES stock"+ "/n");
        applyStockDelta(computation.quantityByProduct(), computation.productsById(), -1);

        System.out.println("ANTES save"+ "/n");
        Sale saved = saleRepository.save(sale);

        System.out.println("ANTES response"+ "/n");
        return toResponse(saved);
    }

    @Transactional
    public SaleResponse update(UUID saleId, UpdateSaleRequest request) {

        // 1. Buscar venta
        Sale sale = saleRepository.findByIdWithDetails(saleId)
                .orElseThrow(() -> new ResourceNotFoundException("Sale not found: " + saleId));

        if (request.items() == null || request.items().isEmpty()) {
            throw new ConflictException("Sale items cannot be empty");
        }

        // 2. Restaurar stock anterior (rollback de venta vieja)
        restoreStockFromDetails(sale.getDetails());

        // 3. Recalcular nueva venta (VALIDA TODO)
        SaleComputation computation = computeSale(request.clientId(),request.items());

        // 4. Actualizar datos de la venta
        sale.setUserId(request.userId());
        sale.setTotal(computation.total());

        // 5. Limpiar y reconstruir detalles
        sale.getDetails().clear();
        sale.getDetails().addAll(buildSaleDetails(sale, computation.lines()));

        // 6. Descontar stock nuevo
        applyStockDelta(computation.quantityByProduct(), computation.productsById(), -1);

        // 7. Guardar
        Sale saved = saleRepository.save(sale);

        return toResponse(saved);
    }

    @Transactional
    public void delete(UUID saleId) {
        Sale sale = saleRepository.findByIdWithDetails(saleId)
                .orElseThrow(() -> new ResourceNotFoundException("Sale not found: " + saleId));

        restoreStockFromDetails(sale.getDetails());
        saleRepository.delete(sale);
    }

    private SaleComputation computeSale(UUID clientId, List<SaleItemRequest> items) {
        if (clientId == null) {
            throw new ConflictException("clientId is required");
        }
        Client client = clientRepository.findById(clientId)
        .orElseThrow(() -> new ResourceNotFoundException("Client not found"));
        ClientType clientType = client.getType();
        ProductPriceType priceType;

        switch (clientType) {
            case DETAIL -> priceType = ProductPriceType.DETAIL;
            case WHOLESALE -> priceType = ProductPriceType.WHOLESALE;
            default -> throw new IllegalStateException("Unknown client type: " + clientType);
        }
        Map<UUID, Integer> requestedQuantities = aggregateRequestedQuantities(items);
        Set<UUID> productIds = requestedQuantities.keySet();

        Map<UUID, Product> productsById = productRepository.findAllById(productIds)
                .stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        if (productsById.size() != productIds.size()) {
            throw new ResourceNotFoundException("One or more product IDs do not exist");
        }

        validateStockAvailability(requestedQuantities, productsById);

        List<SaleLineData> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (SaleItemRequest item : items) {
            Product product = productsById.get(item.productId());
            if (product == null) {
                throw new ResourceNotFoundException("Product not found: " + item.productId());
            }

            ProductPrice productPrice = productPriceRepository
                .findByProductIdAndType(product.getId(), priceType)
                .orElseThrow(() -> new ConflictException(
                    "No price defined for productId " + product.getId()
                ));

            BigDecimal price = productPrice.getPrice();
            if (price == null) {
                throw new ConflictException("Product price is null: " + product.getName());
            }

            BigDecimal subtotal = price.multiply(BigDecimal.valueOf(item.quantity()));
            lines.add(new SaleLineData(product, item.quantity(), price, subtotal));
            total = total.add(subtotal);
        }

        return new SaleComputation(requestedQuantities, productsById, lines, total);
    }

    private Map<UUID, Integer> aggregateRequestedQuantities(List<SaleItemRequest> items) {
        Map<UUID, Integer> requestedQuantities = new LinkedHashMap<>();
        for (SaleItemRequest item : items) {
            requestedQuantities.merge(item.productId(), item.quantity(), Integer::sum);
        }
        return requestedQuantities;
    }

    private void validateStockAvailability(Map<UUID, Integer> requested, Map<UUID, Product> productsById) {
        for (Map.Entry<UUID, Integer> entry : requested.entrySet()) {
            Product product = productsById.get(entry.getKey());
            Integer requiredQuantity = entry.getValue();
            if (product.getStock() < requiredQuantity) {
                throw new ConflictException(
                        "Insufficient stock for product '" + product.getName() +
                                "'. Available: " + product.getStock() +
                                ", requested: " + requiredQuantity
                );
            }
        }
    }

    private void applyStockDelta(Map<UUID, Integer> quantities, Map<UUID, Product> productsById, int multiplier) {
        for (Map.Entry<UUID, Integer> entry : quantities.entrySet()) {
            Product product = productsById.get(entry.getKey());
            int newStock = product.getStock() + (entry.getValue() * multiplier);
            product.setStock(newStock);
        }
    }

    private void restoreStockFromDetails(List<SaleDetail> details) {
        Map<UUID, Integer> soldQuantities = new LinkedHashMap<>();
        Map<UUID, Product> productsById = new LinkedHashMap<>();

        for (SaleDetail detail : details) {
            UUID productId = detail.getProduct().getId();
            soldQuantities.merge(productId, detail.getQuantity(), Integer::sum);
            productsById.put(productId, detail.getProduct());
        }

        applyStockDelta(soldQuantities, productsById, 1);
    }

    private List<SaleDetail> buildSaleDetails(Sale sale, List<SaleLineData> lines) {
        List<SaleDetail> details = new ArrayList<>();
        for (SaleLineData line : lines) {
            details.add(SaleDetail.builder()
                    .sale(sale)
                    .product(line.product())
                    .quantity(line.quantity())
                    .price(line.price())
                    .subtotal(line.subtotal())
                    .build());
        }
        return details;
    }

    private SaleResponse toResponse(Sale sale) {
        List<SaleDetailResponse> details = sale.getDetails().stream()
                .map(detail -> new SaleDetailResponse(
                        detail.getProduct().getId(),
                        detail.getProduct().getName(),
                        detail.getQuantity(),
                        detail.getPrice(),
                        detail.getSubtotal()
                ))
                .toList();

        return new SaleResponse(
                sale.getId(),
                sale.getTotal(),
                sale.getUserId(),
                sale.getCreatedAt(),
                details
        );
    }

    private record SaleLineData(
            Product product,
            Integer quantity,
            BigDecimal price,
            BigDecimal subtotal
    ) {
    }

    private record SaleComputation(
            Map<UUID, Integer> quantityByProduct,
            Map<UUID, Product> productsById,
            List<SaleLineData> lines,
            BigDecimal total
    ) {
    }

    @Transactional
    public SaleResponse updateStatus(UUID saleId, Sale.SaleStatus newStatus) {

        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new ResourceNotFoundException("Sale not found"));

        // 🔒 BLOQUEO 1: si ya está cancelada no se puede cambiar
        if (sale.getStatus() == Sale.SaleStatus.CANCELLED) {
            throw new ConflictException("Cannot modify a cancelled sale");
        }

        // 🔒 BLOQUEO 2: si ya está pagada no se puede cancelar
        if (sale.getStatus() == Sale.SaleStatus.PAID && newStatus == Sale.SaleStatus.CANCELLED) {
            throw new ConflictException("Cannot cancel a paid sale");
        }

        sale.setStatus(newStatus);

        Sale saved = saleRepository.save(sale);

        return new SaleResponse(
            saved.getId(),
            saved.getTotal(),
            saved.getUserId(),
            saved.getCreatedAt(),
            mapDetails(saved.getDetails())
        );
    }

    private List<SaleDetailResponse> mapDetails(List<SaleDetail> details) {
        return details.stream()
                .map(d -> new SaleDetailResponse(
                        d.getProduct().getId(),
                        d.getProduct().getName(),
                        d.getQuantity(),
                        d.getPrice(),
                        d.getSubtotal()
                ))
                .toList();
    }
}
