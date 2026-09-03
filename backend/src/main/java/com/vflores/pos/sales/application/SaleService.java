package com.vflores.pos.sales.application;

import com.vflores.pos.products.domain.model.Product;
import com.vflores.pos.products.domain.repository.ProductRepository;
import com.vflores.pos.sales.api.dto.CreateSaleRequest;
import com.vflores.pos.sales.api.dto.CreateSalePaymentRequest;
import com.vflores.pos.sales.api.dto.SaleDetailResponse;
import com.vflores.pos.sales.api.dto.SaleItemRequest;
import com.vflores.pos.sales.api.dto.SalePaymentResponse;
import com.vflores.pos.sales.api.dto.SalePaymentMovementResponse;
import com.vflores.pos.sales.api.dto.SaleResponse;
import com.vflores.pos.sales.api.dto.UpdateSaleRequest;
import com.vflores.pos.sales.domain.model.Sale;
import com.vflores.pos.sales.domain.model.SaleDetail;
import com.vflores.pos.sales.domain.model.SalePayment;
import com.vflores.pos.sales.domain.repository.SalePaymentRepository;
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
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.Comparator;
import java.util.HashSet;

import static com.vflores.pos.shared.application.AuthenticatedUserSupport.getCurrentUserId;
import static com.vflores.pos.shared.application.InventoryAdjustmentSupport.aggregateQuantities;
import static com.vflores.pos.shared.application.InventoryAdjustmentSupport.applyStockDelta;
import static com.vflores.pos.shared.application.InventoryAdjustmentSupport.restoreStockFromDetails;
import static com.vflores.pos.shared.application.PaymentValidationSupport.requirePositiveAmount;
import static com.vflores.pos.shared.application.PaymentValidationSupport.requireValidPeriod;

@Service
@RequiredArgsConstructor
public class SaleService {

    private final SaleRepository saleRepository;
    private final ProductRepository productRepository;
    private final ClientRepository clientRepository;
    private final ProductPriceRepository productPriceRepository;
    private final SalePaymentRepository salePaymentRepository;
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
        SaleComputation computation = computeSale(request.clientId(), request.items());
        Long nextInvoiceNumber = saleRepository.findMaxInvoiceNumber() + 1;
        Sale.PaymentMethod paymentMethod = request.paymentMethod() == null
                ? Sale.PaymentMethod.CASH
                : request.paymentMethod();

        UUID currentUserId = getCurrentUserId();
        
        Sale sale = Sale.builder()
                .invoiceNumber(nextInvoiceNumber)
                .userId(currentUserId)
                .clientId(request.clientId())
                .paymentMethod(paymentMethod)
                .total(computation.total())
                .status(Sale.SaleStatus.PENDING)
                .details(new ArrayList<>())
                .comments(request.comments())
                .build();

        sale.getDetails().addAll(buildSaleDetails(sale, computation.lines()));
        applyStockDelta(computation.quantityByProduct(), computation.productsById(), -1);
        Sale saved = saleRepository.save(sale);
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
        restoreStockFromDetails(sale.getDetails(), SaleDetail::getProduct, SaleDetail::getQuantity);

        // 3. Recalcular nueva venta (VALIDA TODO)
        SaleComputation computation = computeSale(request.clientId(),request.items());

        if (request.status() != null) {
    sale.setStatus(request.status());
} else {
    sale.setStatus(Sale.SaleStatus.PENDING);
}
        // 4. Actualizar datos de la venta
        sale.setUserId(getCurrentUserId());
        sale.setClientId(request.clientId());
        sale.setPaymentMethod(request.paymentMethod() == null ? Sale.PaymentMethod.CASH : request.paymentMethod());
        sale.setTotal(computation.total());
        sale.setComments(request.comments());

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

        restoreStockFromDetails(sale.getDetails(), SaleDetail::getProduct, SaleDetail::getQuantity);
        saleRepository.delete(sale);
    }

    private SaleComputation computeSale(UUID clientId, List<SaleItemRequest> items) {
        if (clientId == null) {
            throw new ConflictException("clientId is required");
        }
        Client client = clientRepository.findById(clientId)
        .orElseThrow(() -> new ResourceNotFoundException("Client not found: " + clientId));
        ClientType clientType = client.getType();
        ProductPriceType priceType;

        switch (clientType) {
            case DETAIL -> priceType = ProductPriceType.DETAIL;
            case WHOLESALE -> priceType = ProductPriceType.WHOLESALE;
            case NEW -> priceType = ProductPriceType.NEW; // 👈
            default -> throw new IllegalStateException("Unknown client type: " + clientType);
        }
Map<UUID, BigDecimal> requestedQuantities = aggregateQuantities(
                items, SaleItemRequest::productId, SaleItemRequest::quantity);        Set<UUID> productIds = requestedQuantities.keySet();

        Map<UUID, Product> productsById = productRepository.findAllById(productIds)
                .stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        if (productsById.size() != productIds.size()) {
            throw new ResourceNotFoundException("One or more product IDs do not exist");
        }

        //validateStockAvailability(requestedQuantities, productsById);

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
                    "No price defined for product '" + product.getName() + "' and client type " + priceType
                ));

            BigDecimal price = item.price() != null 
                ? item.price()
                : productPrice.getPrice();
            if (price == null) {
                throw new ConflictException("Product price is null: " + product.getName());
            }

BigDecimal subtotal = price.multiply(item.quantity());            lines.add(new SaleLineData(product, item.quantity(), price, subtotal));
            total = total.add(subtotal);
        }

        return new SaleComputation(requestedQuantities, productsById, lines, total);
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

    private List<SaleDetail> buildSaleDetails(Sale sale, List<SaleLineData> lines) {
    List<SaleDetail> details = new ArrayList<>();
    for (int i = 0; i < lines.size(); i++) {
        SaleLineData line = lines.get(i);
        details.add(SaleDetail.builder()
                .sale(sale)
                .product(line.product())
                .quantity(line.quantity())
                .price(line.price())
                .subtotal(line.subtotal())
                .sortOrder(i)  // 👈 agregar
                .build());
    }
    return details;
}

    private SaleResponse toResponse(Sale sale) {
        List<SaleDetailResponse> details = sale.getDetails().stream()
        .sorted(Comparator.comparingInt(SaleDetail::getSortOrder))  // 👈 agregar
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
                sale.getInvoiceNumber(),
                sale.getTotal(),
                sale.getUserId(),
                sale.getClientId(),
                sale.getPaymentMethod(),
                sale.getStatus(),
                sale.getCreatedAt(),
                details,
                mapPayments(sale.getPayments()),
                sale.getComments()
        );
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
            saved.getInvoiceNumber(),
            saved.getTotal(),
            saved.getUserId(),
            saved.getClientId(),
            saved.getPaymentMethod(),
            saved.getStatus(),
            saved.getCreatedAt(),
            mapDetails(saved.getDetails()),
            mapPayments(saved.getPayments()),
            saved.getComments()
        );
    }

    @Transactional
    public SaleResponse savePayments(UUID saleId, List<CreateSalePaymentRequest> payments) {
        Sale sale = saleRepository.findByIdWithDetails(saleId)
                .orElseThrow(() -> new ResourceNotFoundException("Sale not found: " + saleId));

        List<CreateSalePaymentRequest> requestedPayments = payments == null ? List.of() : payments;
        boolean identifiedRequest = requestedPayments.stream().anyMatch(request -> request.id() != null);
        Set<UUID> referencedIds = new HashSet<>();
        Set<UUID> legacyMatches = new HashSet<>();

        for (CreateSalePaymentRequest request : requestedPayments) {
            requirePositiveAmount(request.amount());
            if (request.id() != null) {
                if (!referencedIds.add(request.id())) {
                    throw new ConflictException("Payment cannot be referenced more than once");
                }
                SalePayment existing = sale.getPayments().stream()
                        .filter(payment -> request.id().equals(payment.getId()))
                        .findFirst()
                        .orElseThrow(() -> new ConflictException("Payment does not belong to this sale"));
                if (existing.getMethod() != request.method()
                        || existing.getAmount().compareTo(request.amount()) != 0) {
                    throw new ConflictException("Existing payments cannot be modified");
                }
                continue;
            }

            if (!identifiedRequest) {
                SalePayment legacyMatch = sale.getPayments().stream()
                        .filter(payment -> !legacyMatches.contains(payment.getId()))
                        .filter(payment -> payment.getMethod() == request.method())
                        .filter(payment -> payment.getAmount().compareTo(request.amount()) == 0)
                        .findFirst()
                        .orElse(null);
                if (legacyMatch != null) {
                    legacyMatches.add(legacyMatch.getId());
                    continue;
                }
            }

            sale.getPayments().add(SalePayment.builder()
                    .sale(sale)
                    .method(request.method())
                    .amount(request.amount())
                    .build());
        }

        BigDecimal paidAmount = sale.getPayments().stream()
                .map(SalePayment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (paidAmount.compareTo(sale.getTotal()) > 0) {
            throw new ConflictException("Payment total cannot exceed sale total");
        }

        if (paidAmount.compareTo(BigDecimal.ZERO) == 0) {
            sale.setStatus(Sale.SaleStatus.PENDING);
        } else if (paidAmount.compareTo(sale.getTotal()) < 0) {
            sale.setStatus(Sale.SaleStatus.PARTIAL);
        } else {
            sale.setStatus(Sale.SaleStatus.PAID);
        }

        Sale saved = saleRepository.saveAndFlush(sale);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<SalePaymentMovementResponse> findPaymentMovements(OffsetDateTime from, OffsetDateTime to) {
        requireValidPeriod(from, to);
        return salePaymentRepository
                .findByCreatedAtGreaterThanEqualAndCreatedAtLessThanEqualOrderByCreatedAtAsc(from, to)
                .stream()
                .map(payment -> new SalePaymentMovementResponse(
                        payment.getId(),
                        payment.getSale().getId(),
                        payment.getSale().getInvoiceNumber(),
                        payment.getSale().getClientId(),
                        payment.getSale().getCreatedAt(),
                        payment.getMethod(),
                        payment.getAmount(),
                        payment.getCreatedAt()))
                .toList();
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

    private List<SalePaymentResponse> mapPayments(List<SalePayment> payments) {
        return payments.stream()
                .map(payment -> new SalePaymentResponse(
                        payment.getId(),
                        payment.getSale().getId(),
                        payment.getMethod(),
                        payment.getAmount(),
                        payment.getCreatedAt()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public Long getNextInvoiceNumber() {
        return saleRepository.findMaxInvoiceNumber() + 1;
    }
}
