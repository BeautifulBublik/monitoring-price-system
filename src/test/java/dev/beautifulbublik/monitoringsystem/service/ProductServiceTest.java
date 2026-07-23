package dev.beautifulbublik.monitoringsystem.service;

import dev.beautifulbublik.monitoringsystem.dto.CreateProductRequest;
import dev.beautifulbublik.monitoringsystem.dto.ProductResponse;
import dev.beautifulbublik.monitoringsystem.entity.PriceHistory;
import dev.beautifulbublik.monitoringsystem.entity.Product;
import dev.beautifulbublik.monitoringsystem.entity.ThresholdBase;
import dev.beautifulbublik.monitoringsystem.entity.ThresholdType;
import dev.beautifulbublik.monitoringsystem.entity.TrackingStatus;
import dev.beautifulbublik.monitoringsystem.entity.User;
import dev.beautifulbublik.monitoringsystem.exception.BadRequestException;
import dev.beautifulbublik.monitoringsystem.exception.ConflictException;
import dev.beautifulbublik.monitoringsystem.exception.ResourceNotFoundException;
import dev.beautifulbublik.monitoringsystem.parser.ParsedPrice;
import dev.beautifulbublik.monitoringsystem.parser.PriceParsingException;
import dev.beautifulbublik.monitoringsystem.parser.PriceParsingService;
import dev.beautifulbublik.monitoringsystem.parser.shop.ShopRuleRegistry;
import dev.beautifulbublik.monitoringsystem.repository.PriceHistoryRepository;
import dev.beautifulbublik.monitoringsystem.repository.ProductRepository;
import dev.beautifulbublik.monitoringsystem.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    private static final Long USER_ID = 1L;
    private static final String URL = "https://example-shop.com/product/42";

    @Mock
    private ProductRepository productRepository;
    @Mock
    private PriceHistoryRepository priceHistoryRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PriceParsingService priceParsingService;
    @Mock
    private ShopRuleRegistry shopRuleRegistry;
    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private ProductService productService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User("user@example.com", "hash");

        lenient().when(transactionTemplate.execute(any()))
                .thenAnswer(invocation -> invocation.getArgument(0, TransactionCallback.class)
                        .doInTransaction(null));
    }

    private CreateProductRequest anyDropRequest() {
        return new CreateProductRequest(URL, ThresholdType.ANY_DROP, ThresholdBase.LAST_PRICE, null);
    }

    @Test
    @DisplayName("Adding a product parses the page and stores the first history point")
    void createParsesAndStoresFirstPricePoint() {
        when(productRepository.existsByUserIdAndUrl(USER_ID, URL)).thenReturn(false);
        when(shopRuleRegistry.resolveShopName(URL)).thenReturn("Example Shop");
        when(priceParsingService.parse(URL))
                .thenReturn(new ParsedPrice("Laptop", new BigDecimal("89990.00"), "UAH"));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(productRepository.save(any(Product.class))).thenAnswer(i -> i.getArgument(0));

        ProductResponse response = productService.create(USER_ID, anyDropRequest());

        assertThat(response.title()).isEqualTo("Laptop");
        assertThat(response.shopName()).isEqualTo("Example Shop");
        assertThat(response.currentPrice()).isEqualByComparingTo("89990.00");
        assertThat(response.currency()).isEqualTo("UAH");

        ArgumentCaptor<PriceHistory> historyCaptor = ArgumentCaptor.forClass(PriceHistory.class);
        verify(priceHistoryRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getPrice()).isEqualByComparingTo("89990.00");
        assertThat(historyCaptor.getValue().getCurrency()).isEqualTo("UAH");
    }

    @Test
    @DisplayName("A product with the same URL cannot be added twice")
    void createRejectsDuplicateUrl() {
        when(productRepository.existsByUserIdAndUrl(USER_ID, URL)).thenReturn(true);

        assertThatThrownBy(() -> productService.create(USER_ID, anyDropRequest()))
                .isInstanceOf(ConflictException.class);
        verify(priceParsingService, never()).parse(anyString());
    }

    @Test
    @DisplayName("If the price could not be parsed, the product is not created")
    void createDoesNotPersistWhenParsingFails() {
        when(productRepository.existsByUserIdAndUrl(USER_ID, URL)).thenReturn(false);
        when(shopRuleRegistry.resolveShopName(URL)).thenReturn("Example Shop");
        when(priceParsingService.parse(URL)).thenThrow(new PriceParsingException("Site unreachable"));

        assertThatThrownBy(() -> productService.create(USER_ID, anyDropRequest()))
                .isInstanceOf(PriceParsingException.class);

        verify(productRepository, never()).save(any());
        verify(priceHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("PERCENT without a threshold value — a validation error, not an NPE at price-check time")
    void createRejectsPercentWithoutThresholdValue() {
        CreateProductRequest request =
                new CreateProductRequest(URL, ThresholdType.PERCENT, ThresholdBase.LAST_PRICE, null);

        assertThatThrownBy(() -> productService.create(USER_ID, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("thresholdValue");

        verify(priceParsingService, never()).parse(anyString());
    }

    @Test
    @DisplayName("Someone else's product is inaccessible: we return 404, not 403")
    void findByIdHidesForeignProduct() {
        when(productRepository.findByIdAndUserId(99L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.findById(USER_ID, 99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("The product list shows the last known price")
    void findAllAttachesLatestPrice() {
        Product product = new Product(user, URL, "Example Shop");
        product.setTitle("Laptop");

        when(productRepository.findByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of(product));
        when(priceHistoryRepository.findFirstByProductIdOrderByCheckedAtDesc(any()))
                .thenReturn(Optional.of(new PriceHistory(
                        product, new BigDecimal("75000.00"), "UAH", java.time.Instant.now())));

        List<ProductResponse> products = productService.findAll(USER_ID);

        assertThat(products).hasSize(1);
        assertThat(products.getFirst().currentPrice()).isEqualByComparingTo("75000.00");
    }

    @Test
    @DisplayName("A product with no price history does not break the list — the price is simply null")
    void findAllHandlesProductWithoutHistory() {
        Product product = new Product(user, URL, "Example Shop");

        when(productRepository.findByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of(product));
        when(priceHistoryRepository.findFirstByProductIdOrderByCheckedAtDesc(any()))
                .thenReturn(Optional.empty());

        List<ProductResponse> products = productService.findAll(USER_ID);

        assertThat(products.getFirst().currentPrice()).isNull();
        assertThat(products.getFirst().currency()).isNull();
    }

    @Test
    @DisplayName("Pausing tracking changes the status without touching the history")
    void updatePausesTracking() {
        Product product = new Product(user, URL, "Example Shop");
        when(productRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(product));
        when(priceHistoryRepository.findFirstByProductIdOrderByCheckedAtDesc(any()))
                .thenReturn(Optional.empty());

        ProductResponse response = productService.update(USER_ID, 1L,
                new dev.beautifulbublik.monitoringsystem.dto.UpdateProductRequest(
                        TrackingStatus.PAUSED, null, null, null));

        assertThat(response.trackingStatus()).isEqualTo(TrackingStatus.PAUSED);
        assertThat(product.getTrackingStatus()).isEqualTo(TrackingStatus.PAUSED);
        verify(priceHistoryRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Deletion is available only to the owner")
    void deleteRequiresOwnership() {
        when(productRepository.findByIdAndUserId(anyLong(), anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.delete(USER_ID, 42L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(productRepository, never()).delete(any());
    }
}
