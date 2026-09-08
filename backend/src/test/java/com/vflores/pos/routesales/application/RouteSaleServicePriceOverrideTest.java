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
import com.vflores.pos.routesales.api.dto.CreateRouteSaleRequest;
import com.vflores.pos.routesales.api.dto.RouteSaleItemRequest;
import com.vflores.pos.routesales.api.dto.RouteSaleResponse;
import com.vflores.pos.routesales.api.dto.UpdateRouteSaleRequest;
import com.vflores.pos.routesales.domain.model.RouteSale;
import com.vflores.pos.routesales.domain.model.RouteSaleDetail;
import com.vflores.pos.routesales.domain.repository.RouteSalePaymentRepository;
import com.vflores.pos.routesales.domain.repository.RouteSaleRepository;
import com.vflores.pos.sales.domain.model.Sale;
import com.vflores.pos.shared.application.PriceOverrideGuard;
import com.vflores.pos.shared.exception.ConflictException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RouteSaleServicePriceOverrideTest {

    private static final UUID CLIENT_ID = UUID.fromString("c12c5f65-ced9-482e-a7ca-29460bf19748");
    private static final UUID DRIVER_ID = UUID.fromString("9b1e71d0-84a4-4e7b-9d2a-2f6d1b0c8f3a");
    private static final UUID PRODUCT_ID = UUID.fromString("3aac17f9-53e2-4104-830b-94a6ef05547d");
    private static final UUID USER_ID = UUID.fromString("e7a159f0-5e19-4b3e-9ce0-94a6ef05547d");
    private static final UUID ROUTE_ID = UUID.fromString("28343428-e94c-4ec4-a256-f132daf743f5");
    private static final BigDecimal CATALOG_PRICE = new BigDecimal("10.00");

    private RouteSaleRepository routeSaleRepository;
    private ProductRepository productRepository;
    private ClientRepository clientRepository;
    private ProductPriceRepository productPriceRepository;
    private RouteSaleService routeSaleService;

    @BeforeEach
    void setUp() {
        routeSaleRepository = mock(RouteSaleRepository.class);
        RouteSalePaymentRepository routeSalePaymentRepository = mock(RouteSalePaymentRepository.class);
        productRepository = mock(ProductRepository.class);
        clientRepository = mock(ClientRepository.class);
        DriverRepository driverRepository = mock(DriverRepository.class);
        productPriceRepository = mock(ProductPriceRepository.class);

        routeSaleService = new RouteSaleService(
                routeSaleRepository, routeSalePaymentRepository, productRepository,
                clientRepository, driverRepository, productPriceRepository, new PriceOverrideGuard());

        Client client = Client.builder().id(CLIENT_ID).type(ClientType.DETAIL).build();
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(client));
        when(driverRepository.existsById(DRIVER_ID)).thenReturn(true);

        Product product = Product.builder().id(PRODUCT_ID).name("Planta").stock(100).build();
        when(productRepository.findAllById(Set.of(PRODUCT_ID))).thenReturn(List.of(product));

        ProductPrice productPrice = ProductPrice.builder()
                .id(UUID.randomUUID()).product(product).type(ProductPriceType.DETAIL)
                .price(CATALOG_PRICE).build();
        when(productPriceRepository.findByProductIdAndType(PRODUCT_ID, ProductPriceType.DETAIL))
                .thenReturn(Optional.of(productPrice));

        when(routeSaleRepository.findMaxInvoiceNumber()).thenReturn(0L);
        when(routeSaleRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(String... authorities) {
        Set<GrantedAuthority> granted = Set.<GrantedAuthority>of(
                java.util.Arrays.stream(authorities)
                        .map(SimpleGrantedAuthority::new)
                        .toArray(GrantedAuthority[]::new));
        AuthenticatedUser user = new AuthenticatedUser(
                USER_ID, "juan", "", "", "Juan", true, false, granted);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private CreateRouteSaleRequest createRequest(BigDecimal price) {
        return new CreateRouteSaleRequest(
                CLIENT_ID, DRIVER_ID, Sale.PaymentMethod.CASH,
                List.of(new RouteSaleItemRequest(PRODUCT_ID, BigDecimal.ONE, price)), "test");
    }

    @Test
    void createWithoutPriceUsesCatalogPriceAndBackendTotals() {
        authenticate("ROUTE_CREATE");
        RouteSaleResponse response = routeSaleService.create(createRequest(null));
        assertEquals(CATALOG_PRICE, response.details().get(0).price());
        assertEquals(CATALOG_PRICE, response.total());
    }

    @Test
    void createWithPriceEqualToCatalogIgnoringScaleIsAllowed() {
        authenticate("ROUTE_CREATE");
        RouteSaleResponse response = routeSaleService.create(createRequest(new BigDecimal("10.0")));
        assertEquals(0, CATALOG_PRICE.compareTo(response.total()));
    }

    @Test
    void createWithDifferentPriceWithoutOverridePermissionIsDenied() {
        authenticate("ROUTE_CREATE");
        assertThrows(AccessDeniedException.class,
                () -> routeSaleService.create(createRequest(new BigDecimal("5.00"))));
    }

    @Test
    void createWithDifferentPriceWithOverridePermissionIsAllowed() {
        authenticate("ROUTE_CREATE", "ROUTE_PRICE_OVERRIDE");
        RouteSaleResponse response = routeSaleService.create(createRequest(new BigDecimal("5.00")));
        assertEquals(new BigDecimal("5.00"), response.details().get(0).price());
        assertEquals(new BigDecimal("5.00"), response.total());
    }

    @Test
    void createWithZeroOrNegativePriceIsRejectedAlways() {
        authenticate("ROUTE_CREATE", "ROUTE_PRICE_OVERRIDE");
        assertThrows(ConflictException.class,
                () -> routeSaleService.create(createRequest(BigDecimal.ZERO)));
        assertThrows(ConflictException.class,
                () -> routeSaleService.create(createRequest(new BigDecimal("-1000"))));
    }

    private void stubExistingRouteSale(BigDecimal historicalPrice) {
        RouteSale routeSale = RouteSale.builder()
                .id(ROUTE_ID).invoiceNumber(7L).userId(USER_ID).clientId(CLIENT_ID)
                .driverId(DRIVER_ID).paymentMethod(Sale.PaymentMethod.CASH)
                .total(historicalPrice)
                .status(RouteSale.RouteStatus.PENDING)
                .comments("")
                .details(new ArrayList<>())
                .build();
        RouteSaleDetail detail = RouteSaleDetail.builder()
                .id(UUID.randomUUID()).routeSale(routeSale).product(Product.builder()
                        .id(PRODUCT_ID).name("Planta").stock(100).build())
                .quantity(BigDecimal.ONE).price(historicalPrice)
                .subtotal(historicalPrice).build();
        routeSale.getDetails().add(detail);
        when(routeSaleRepository.findByIdWithDetails(ROUTE_ID)).thenReturn(Optional.of(routeSale));
    }

    private UpdateRouteSaleRequest updateRequest(BigDecimal price) {
        return new UpdateRouteSaleRequest(
                CLIENT_ID, DRIVER_ID, Sale.PaymentMethod.CASH,
                List.of(new RouteSaleItemRequest(PRODUCT_ID, BigDecimal.ONE, price)), "edit");
    }

    @Test
    void updatePreservingHistoricalPriceIsAllowedWithoutOverridePermission() {
        authenticate("ROUTE_CREATE", "ROUTE_UPDATE");
        stubExistingRouteSale(new BigDecimal("9.50"));
        RouteSaleResponse response = routeSaleService.update(ROUTE_ID, updateRequest(new BigDecimal("9.50")));
        assertEquals(new BigDecimal("9.50"), response.total());
    }

    @Test
    void updateWithDifferentPriceWithoutOverridePermissionIsDenied() {
        authenticate("ROUTE_CREATE", "ROUTE_UPDATE");
        stubExistingRouteSale(new BigDecimal("9.50"));
        assertThrows(AccessDeniedException.class,
                () -> routeSaleService.update(ROUTE_ID, updateRequest(new BigDecimal("5.00"))));
    }

    @Test
    void updateWithDifferentPriceWithOverridePermissionIsAllowed() {
        authenticate("ROUTE_CREATE", "ROUTE_UPDATE", "ROUTE_PRICE_OVERRIDE");
        stubExistingRouteSale(new BigDecimal("9.50"));
        RouteSaleResponse response = routeSaleService.update(ROUTE_ID, updateRequest(new BigDecimal("5.00")));
        assertEquals(new BigDecimal("5.00"), response.details().get(0).price());
        assertEquals(new BigDecimal("5.00"), response.total());
    }
}