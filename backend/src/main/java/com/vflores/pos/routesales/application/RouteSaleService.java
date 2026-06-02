package com.vflores.pos.routesales.application;

import com.vflores.pos.auth.infrastructure.security.AuthenticatedUser;
import com.vflores.pos.clients.domain.model.Client;
import com.vflores.pos.clients.domain.model.ClientType;
import com.vflores.pos.clients.domain.repository.ClientRepository;
import com.vflores.pos.drivers.domain.repository.DriverRepository;
import com.vflores.pos.products.domain.model.Product;
import com.vflores.pos.products.domain.model.ProductPrice;
import com.vflores.pos.products.domain.model.ProductPriceType;
import com.vflores.pos.products.domain.repository.ProductPriceRepository;
import com.vflores.pos.products.domain.repository.ProductRepository;
import com.vflores.pos.routesales.api.dto.CreateRouteSalePaymentRequest;
import com.vflores.pos.routesales.api.dto.CreateRouteSaleRequest;
import com.vflores.pos.routesales.api.dto.RouteSaleDetailResponse;
import com.vflores.pos.routesales.api.dto.RouteSaleItemRequest;
import com.vflores.pos.routesales.api.dto.RouteSalePaymentResponse;
import com.vflores.pos.routesales.api.dto.RouteSaleResponse;
import com.vflores.pos.routesales.api.dto.UpdateRouteSaleRequest;
import com.vflores.pos.routesales.domain.model.RouteSale;
import com.vflores.pos.routesales.domain.model.RouteSaleDetail;
import com.vflores.pos.routesales.domain.model.RouteSalePayment;
import com.vflores.pos.routesales.domain.repository.RouteSalePaymentRepository;
import com.vflores.pos.routesales.domain.repository.RouteSaleRepository;
import com.vflores.pos.shared.exception.ConflictException;
import com.vflores.pos.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.Comparator;

@Service
@RequiredArgsConstructor
public class RouteSaleService {

    private final RouteSaleRepository routeSaleRepository;
    private final RouteSalePaymentRepository routeSalePaymentRepository;
    private final ProductRepository productRepository;
    private final ClientRepository clientRepository;
    private final DriverRepository driverRepository;
    private final ProductPriceRepository productPriceRepository;

    @Transactional(readOnly = true)
    public List<RouteSaleResponse> findAll() {
        return routeSaleRepository.findAllWithDetails().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public RouteSaleResponse findById(UUID id) {
        RouteSale routeSale = routeSaleRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Route sale not found: " + id));
        return toResponse(routeSale);
    }

    @Transactional
    public RouteSaleResponse create(CreateRouteSaleRequest request) {
        validateDriver(request.driverId());
        SaleComputation computation = computeRouteSale(request.clientId(), request.items());
        Long nextInvoiceNumber = routeSaleRepository.findMaxInvoiceNumber() + 1;

        RouteSale routeSale = RouteSale.builder()
                .invoiceNumber(nextInvoiceNumber)
                .userId(getCurrentUserId())
                .clientId(request.clientId())
                .driverId(request.driverId())
                .paymentMethod(request.paymentMethod() == null ? com.vflores.pos.sales.domain.model.Sale.PaymentMethod.CASH : request.paymentMethod())
                .total(computation.total())
                .status(RouteSale.RouteStatus.PENDING)
                .comments(request.comments())
                .details(new ArrayList<>())
                .build();

        routeSale.getDetails().addAll(buildDetails(routeSale, computation.lines()));
        applyStockDelta(computation.quantityByProduct(), computation.productsById(), -1);

        RouteSale saved = routeSaleRepository.save(routeSale);
        return toResponse(saved);
    }

    @Transactional
    public RouteSaleResponse update(UUID routeSaleId, UpdateRouteSaleRequest request) {
        RouteSale routeSale = routeSaleRepository.findByIdWithDetails(routeSaleId)
                .orElseThrow(() -> new ResourceNotFoundException("Route sale not found: " + routeSaleId));

        if (request.items() == null || request.items().isEmpty()) {
            throw new ConflictException("Route sale items cannot be empty");
        }

        validateDriver(request.driverId());
        restoreStockFromDetails(routeSale.getDetails());
        SaleComputation computation = computeRouteSale(request.clientId(), request.items());

        routeSale.setUserId(getCurrentUserId());
        routeSale.setClientId(request.clientId());
        routeSale.setDriverId(request.driverId());
        routeSale.setPaymentMethod(request.paymentMethod() == null ? com.vflores.pos.sales.domain.model.Sale.PaymentMethod.CASH : request.paymentMethod());
        routeSale.setComments(request.comments());
        routeSale.setTotal(computation.total());

        routeSale.getDetails().clear();
        routeSale.getDetails().addAll(buildDetails(routeSale, computation.lines()));
        applyStockDelta(computation.quantityByProduct(), computation.productsById(), -1);

        RouteSale saved = routeSaleRepository.save(routeSale);
        return toResponse(saved);
    }

    @Transactional
    public void delete(UUID routeSaleId) {
        RouteSale routeSale = routeSaleRepository.findByIdWithDetails(routeSaleId)
                .orElseThrow(() -> new ResourceNotFoundException("Route sale not found: " + routeSaleId));

        restoreStockFromDetails(routeSale.getDetails());
        routeSaleRepository.delete(routeSale);
    }

    @Transactional
    public RouteSaleResponse savePayments(UUID routeSaleId, List<CreateRouteSalePaymentRequest> payments) {
        RouteSale routeSale = routeSaleRepository.findByIdWithDetails(routeSaleId)
                .orElseThrow(() -> new ResourceNotFoundException("Route sale not found: " + routeSaleId));

        routeSalePaymentRepository.deleteByRouteSaleId(routeSaleId);
        routeSale.getPayments().clear();

        BigDecimal paidAmount = BigDecimal.ZERO;
        if (payments != null) {
            for (CreateRouteSalePaymentRequest request : payments) {
                BigDecimal amount = request.amount();
                if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new ConflictException("Payment amount must be greater than 0");
                }

                RouteSalePayment payment = RouteSalePayment.builder()
                        .routeSale(routeSale)
                        .method(request.method())
                        .amount(amount)
                        .build();
                routeSale.getPayments().add(payment);
                paidAmount = paidAmount.add(amount);
            }
        }

        if (paidAmount.compareTo(routeSale.getTotal()) > 0) {
            throw new ConflictException("Payment total cannot exceed route sale total");
        }

        if (paidAmount.compareTo(routeSale.getTotal()) == 0 && paidAmount.compareTo(BigDecimal.ZERO) > 0) {
            routeSale.setStatus(RouteSale.RouteStatus.PAID);
        } else {
            routeSale.setStatus(RouteSale.RouteStatus.PENDING);
        }

        RouteSale saved = routeSaleRepository.save(routeSale);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Long getNextInvoiceNumber() {
        return routeSaleRepository.findMaxInvoiceNumber() + 1;
    }

    @Transactional
    public RouteSaleResponse updateStatus(UUID routeSaleId, RouteSale.RouteStatus status) {
        RouteSale routeSale = routeSaleRepository.findById(routeSaleId)
                .orElseThrow(() -> new ResourceNotFoundException("Route sale not found: " + routeSaleId));

        routeSale.setStatus(status);
        RouteSale saved = routeSaleRepository.save(routeSale);
        return toResponse(saved);
    }

    private SaleComputation computeRouteSale(UUID clientId, List<RouteSaleItemRequest> items) {
        if (clientId == null) {
            throw new ConflictException("clientId is required");
        }
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found: " + clientId));

        ProductPriceType priceType = client.getType() == ClientType.WHOLESALE
                ? ProductPriceType.WHOLESALE
                : ProductPriceType.DETAIL;

Map<UUID, BigDecimal> requestedQuantities = aggregateRequestedQuantities(items);        Set<UUID> productIds = requestedQuantities.keySet();

        Map<UUID, Product> productsById = productRepository.findAllById(productIds)
                .stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        if (productsById.size() != productIds.size()) {
            throw new ResourceNotFoundException("One or more product IDs do not exist");
        }

        List<SaleLineData> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (RouteSaleItemRequest item : items) {
            Product product = productsById.get(item.productId());
            ProductPrice productPrice = productPriceRepository
                    .findByProductIdAndType(product.getId(), priceType)
                    .orElseThrow(() -> new ConflictException(
                            "No price defined for product '" + product.getName() + "' and client type " + priceType
                    ));

            BigDecimal price = item.price() != null ? item.price() : productPrice.getPrice();
BigDecimal subtotal = price.multiply(item.quantity());lines.add(new SaleLineData(product, item.quantity(), price, subtotal));
total = total.add(subtotal);
        }

        return new SaleComputation(requestedQuantities, productsById, lines, total);
    }

    private Map<UUID, BigDecimal> aggregateRequestedQuantities(List<RouteSaleItemRequest> items) {
    Map<UUID, BigDecimal> requestedQuantities = new LinkedHashMap<>();
    for (RouteSaleItemRequest item : items) {
        requestedQuantities.merge(item.productId(), item.quantity(), BigDecimal::add);
    }
    return requestedQuantities;
}

    private void applyStockDelta(Map<UUID, BigDecimal> quantities, Map<UUID, Product> productsById, int multiplier) {
    for (Map.Entry<UUID, BigDecimal> entry : quantities.entrySet()) {
        Product product = productsById.get(entry.getKey());
        BigDecimal delta = entry.getValue().multiply(BigDecimal.valueOf(multiplier));
        int newStock = product.getStock() + delta.intValue();
        product.setStock(newStock);
    }
}

    private void restoreStockFromDetails(List<RouteSaleDetail> details) {
    Map<UUID, BigDecimal> soldQuantities = new LinkedHashMap<>();
    Map<UUID, Product> productsById = new LinkedHashMap<>();

    for (RouteSaleDetail detail : details) {
        UUID productId = detail.getProduct().getId();
        soldQuantities.merge(productId, detail.getQuantity(), BigDecimal::add);
        productsById.put(productId, detail.getProduct());
    }

    applyStockDelta(soldQuantities, productsById, 1);
}

    private List<RouteSaleDetail> buildDetails(RouteSale routeSale, List<SaleLineData> lines) {
    List<RouteSaleDetail> details = new ArrayList<>();
    for (int i = 0; i < lines.size(); i++) {
        SaleLineData line = lines.get(i);
        details.add(RouteSaleDetail.builder()
                .routeSale(routeSale)
                .product(line.product())
                .quantity(line.quantity())
                .price(line.price())
                .subtotal(line.subtotal())
                .sortOrder(i)  // 👈 agregar
                .build());
    }
    return details;
}

    private RouteSaleResponse toResponse(RouteSale routeSale) {
        List<RouteSaleDetailResponse> details = routeSale.getDetails().stream()
        .sorted(Comparator.comparingInt(RouteSaleDetail::getSortOrder))  
        .map(detail -> new RouteSaleDetailResponse(
                detail.getProduct().getId(),
                detail.getProduct().getName(),
                detail.getQuantity(),
                detail.getPrice(),
                detail.getSubtotal()
        ))
        .toList();

        List<RouteSalePaymentResponse> payments = routeSale.getPayments().stream()
                .map(payment -> new RouteSalePaymentResponse(
                        payment.getId(),
                        payment.getRouteSale().getId(),
                        payment.getMethod(),
                        payment.getAmount(),
                        payment.getCreatedAt()
                ))
                .toList();

        return new RouteSaleResponse(
                routeSale.getId(),
                routeSale.getInvoiceNumber(),
                "R-" + String.format("%03d", routeSale.getInvoiceNumber()),
                routeSale.getUserId(),
                routeSale.getClientId(),
                routeSale.getDriverId(),
                routeSale.getPaymentMethod(),
                routeSale.getTotal(),
                routeSale.getStatus(),
                routeSale.getComments(),
                routeSale.getCreatedAt(),
                details,
                payments
        );
    }

    private UUID getCurrentUserId() {
        var authentication = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        var principal = (AuthenticatedUser) authentication.getPrincipal();
        return principal.getId();
    }

    private void validateDriver(UUID driverId) {
        if (driverId == null) {
            throw new ConflictException("driverId is required");
        }
        if (!driverRepository.existsById(driverId)) {
            throw new ResourceNotFoundException("Driver not found: " + driverId);
        }
    }

    private record SaleLineData(
            Product product,
            BigDecimal quantity,
            BigDecimal price,
            BigDecimal subtotal
    ) {
    }

    private record SaleComputation(
            Map<UUID, BigDecimal> quantityByProduct,
            Map<UUID, Product> productsById,
            List<SaleLineData> lines,
            BigDecimal total
    ) {
    }
}
