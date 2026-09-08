package com.vflores.pos.sales.application;

import com.vflores.pos.auth.infrastructure.security.AuthenticatedUser;
import com.vflores.pos.clients.domain.model.Client;
import com.vflores.pos.clients.domain.model.ClientType;
import com.vflores.pos.clients.domain.repository.ClientRepository;
import com.vflores.pos.products.domain.model.Product;
import com.vflores.pos.products.domain.model.ProductPrice;
import com.vflores.pos.products.domain.model.ProductPriceType;
import com.vflores.pos.products.domain.repository.ProductPriceRepository;
import com.vflores.pos.products.domain.repository.ProductRepository;
import com.vflores.pos.sales.api.dto.CreateSaleRequest;
import com.vflores.pos.sales.api.dto.SaleItemRequest;
import com.vflores.pos.sales.api.dto.SaleResponse;
import com.vflores.pos.sales.api.dto.UpdateSaleRequest;
import com.vflores.pos.sales.domain.model.Sale;
import com.vflores.pos.sales.domain.model.SaleDetail;
import com.vflores.pos.sales.domain.repository.SalePaymentRepository;
import com.vflores.pos.sales.domain.repository.SaleRepository;
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

class SaleServicePriceOverrideTest {

    private static final UUID CLIENT_ID = UUID.fromString("c12c5f65-ced9-482e-a7ca-29460bf19748");
    private static final UUID PRODUCT_ID = UUID.fromString("3aac17f9-53e2-4104-830b-94a6ef05547d");
    private static final UUID USER_ID = UUID.fromString("e7a159f0-5e19-4a1e-9ce0-94a6ef05547d");
    private static final UUID SALE_ID = UUID.fromString("28343428-e94c-4ec4-a256-f132daf743f5");
    private static final BigDecimal CATALOG_PRICE = new BigDecimal("10.00");

    private SaleRepository saleRepository;
    private ProductRepository productRepository;
    private ClientRepository clientRepository;
    private ProductPriceRepository productPriceRepository;
    private SaleService saleService;

    @BeforeEach
    void setUp() {
        saleRepository = mock(SaleRepository.class);
        productRepository = mock(ProductRepository.class);
        clientRepository = mock(ClientRepository.class);
        productPriceRepository = mock(ProductPriceRepository.class);
        SalePaymentRepository salePaymentRepository = mock(SalePaymentRepository.class);
        saleService = new SaleService(
                saleRepository, productRepository, clientRepository, productPriceRepository,
                salePaymentRepository, new PriceOverrideGuard());

        Client client = Client.builder().id(CLIENT_ID).type(ClientType.DETAIL).build();
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(client));

        Product product = Product.builder().id(PRODUCT_ID).name("Planta").stock(100).build();
        when(productRepository.findAllById(Set.of(PRODUCT_ID))).thenReturn(List.of(product));

        ProductPrice productPrice = ProductPrice.builder()
                .id(UUID.randomUUID()).product(product).type(ProductPriceType.DETAIL)
                .price(CATALOG_PRICE).build();
        when(productPriceRepository.findByProductIdAndType(PRODUCT_ID, ProductPriceType.DETAIL))
                .thenReturn(Optional.of(productPrice));

        when(saleRepository.findMaxInvoiceNumber()).thenReturn(0L);
        when(saleRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
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

    private CreateSaleRequest createRequest(BigDecimal price) {
        return new CreateSaleRequest(
                CLIENT_ID, Sale.PaymentMethod.CASH,
                List.of(new SaleItemRequest(PRODUCT_ID, BigDecimal.ONE, price)), "test");
    }

    @Test
    void createWithoutPriceUsesCatalogPriceAndBackendTotals() {
        authenticate("SALE_CREATE");
        SaleResponse response = saleService.create(createRequest(null));
        assertEquals(CATALOG_PRICE, response.details().get(0).price());
        assertEquals(CATALOG_PRICE, response.total());
    }

    @Test
    void createWithPriceEqualToCatalogIgnoringScaleIsAllowed() {
        authenticate("SALE_CREATE");
        SaleResponse response = saleService.create(createRequest(new BigDecimal("10.0")));
        assertEquals(0, CATALOG_PRICE.compareTo(response.total()));
    }

    @Test
    void createWithDifferentPriceWithoutOverridePermissionIsDenied() {
        authenticate("SALE_CREATE");
        assertThrows(AccessDeniedException.class,
                () -> saleService.create(createRequest(new BigDecimal("5.00"))));
    }

    @Test
    void createWithDifferentPriceWithOverridePermissionIsAllowed() {
        authenticate("SALE_CREATE", "SALE_PRICE_OVERRIDE");
        SaleResponse response = saleService.create(createRequest(new BigDecimal("5.00")));
        assertEquals(new BigDecimal("5.00"), response.details().get(0).price());
        assertEquals(new BigDecimal("5.00"), response.total());
    }

    @Test
    void createWithZeroOrNegativePriceIsRejectedAlways() {
        authenticate("SALE_CREATE", "SALE_PRICE_OVERRIDE");
        assertThrows(ConflictException.class,
                () -> saleService.create(createRequest(BigDecimal.ZERO)));
        assertThrows(ConflictException.class,
                () -> saleService.create(createRequest(new BigDecimal("-1000"))));
    }

    private void stubExistingSale(BigDecimal historicalPrice) {
        Sale sale = Sale.builder()
                .id(SALE_ID).invoiceNumber(7L).userId(USER_ID).clientId(CLIENT_ID)
                .paymentMethod(Sale.PaymentMethod.CASH)
                .total(historicalPrice)
                .status(Sale.SaleStatus.PENDING)
                .comments("")
                .details(new ArrayList<>())
                .build();
        SaleDetail detail = SaleDetail.builder()
                .id(UUID.randomUUID()).sale(sale).product(Product.builder()
                        .id(PRODUCT_ID).name("Planta").stock(100).build())
                .quantity(BigDecimal.ONE).price(historicalPrice)
                .subtotal(historicalPrice).sortOrder(0).build();
        sale.getDetails().add(detail);
        when(saleRepository.findByIdWithDetails(SALE_ID)).thenReturn(Optional.of(sale));
    }

    private UpdateSaleRequest updateRequest(BigDecimal price) {
        return new UpdateSaleRequest(
                CLIENT_ID, Sale.PaymentMethod.CASH,
                List.of(new SaleItemRequest(PRODUCT_ID, BigDecimal.ONE, price)), "edit");
    }

    @Test
    void updatePreservingHistoricalPriceIsAllowedWithoutOverridePermission() {
        authenticate("SALE_CREATE", "SALE_UPDATE");
        stubExistingSale(new BigDecimal("9.50"));
        SaleResponse response = saleService.update(SALE_ID, updateRequest(new BigDecimal("9.50")));
        assertEquals(new BigDecimal("9.50"), response.total());
    }

    @Test
    void updateWithDifferentPriceWithoutOverridePermissionIsDenied() {
        authenticate("SALE_CREATE", "SALE_UPDATE");
        stubExistingSale(new BigDecimal("9.50"));
        assertThrows(AccessDeniedException.class,
                () -> saleService.update(SALE_ID, updateRequest(new BigDecimal("5.00"))));
    }

    @Test
    void updateWithDifferentPriceWithOverridePermissionIsAllowed() {
        authenticate("SALE_CREATE", "SALE_UPDATE", "SALE_PRICE_OVERRIDE");
        stubExistingSale(new BigDecimal("9.50"));
        SaleResponse response = saleService.update(SALE_ID, updateRequest(new BigDecimal("5.00")));
        assertEquals(new BigDecimal("5.00"), response.details().get(0).price());
        assertEquals(new BigDecimal("5.00"), response.total());
    }
}