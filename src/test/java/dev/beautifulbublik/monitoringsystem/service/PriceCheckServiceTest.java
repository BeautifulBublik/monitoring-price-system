package dev.beautifulbublik.monitoringsystem.service;

import dev.beautifulbublik.monitoringsystem.config.PriceMonitorProperties;
import dev.beautifulbublik.monitoringsystem.config.PriceMonitorProperties.History.StoreMode;
import dev.beautifulbublik.monitoringsystem.entity.NotificationSettings;
import dev.beautifulbublik.monitoringsystem.entity.PriceHistory;
import dev.beautifulbublik.monitoringsystem.entity.Product;
import dev.beautifulbublik.monitoringsystem.entity.ThresholdType;
import dev.beautifulbublik.monitoringsystem.entity.User;
import dev.beautifulbublik.monitoringsystem.notification.PriceDropNotification;
import dev.beautifulbublik.monitoringsystem.parser.ParsedPrice;
import dev.beautifulbublik.monitoringsystem.parser.PriceParsingException;
import dev.beautifulbublik.monitoringsystem.parser.PriceParsingService;
import dev.beautifulbublik.monitoringsystem.repository.NotificationSettingsRepository;
import dev.beautifulbublik.monitoringsystem.repository.PriceHistoryRepository;
import dev.beautifulbublik.monitoringsystem.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PriceCheckServiceTest {

    private static final Long PRODUCT_ID = 1L;
    private static final String URL = "https://example-shop.com/product/42";

    @Mock
    private ProductRepository productRepository;
    @Mock
    private PriceHistoryRepository priceHistoryRepository;
    @Mock
    private NotificationSettingsRepository settingsRepository;
    @Mock
    private PriceParsingService priceParsingService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private TransactionTemplate transactionTemplate;

    private PriceCheckService priceCheckService;
    private PriceMonitorProperties properties;
    private Product product;
    private User user;

    @BeforeEach
    void setUp() {
        properties = new PriceMonitorProperties();
        properties.getHistory().setStoreMode(StoreMode.ON_CHANGE);

        priceCheckService = new PriceCheckService(
                productRepository, priceHistoryRepository, settingsRepository, priceParsingService,
                new ThresholdEvaluator(), notificationService, transactionTemplate, properties);

        lenient().when(transactionTemplate.execute(any()))
                .thenAnswer(invocation -> invocation.getArgument(0, TransactionCallback.class)
                        .doInTransaction(null));

        user = new User("user@example.com", "hash");
        product = new Product(user, URL, "Example Shop");
        product.setTitle("Laptop");
        product.setThresholdType(ThresholdType.ANY_DROP);
    }

    private void givenParsedPrice(String price) {
        when(priceParsingService.parse(URL))
                .thenReturn(new ParsedPrice("Laptop", new BigDecimal(price), "UAH"));
        lenient().when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
    }

    private void givenPreviousPrice(String price) {
        when(priceHistoryRepository.findFirstByProductIdOrderByCheckedAtDesc(PRODUCT_ID))
                .thenReturn(Optional.of(new PriceHistory(
                        product, new BigDecimal(price), "UAH", Instant.now())));
        lenient().when(priceHistoryRepository.findMinPrice(PRODUCT_ID))
                .thenReturn(Optional.of(new BigDecimal(price)));
    }

    private void givenNotificationSettings() {
        NotificationSettings settings = new NotificationSettings(user);
        settings.setEmailEnabled(true);
        lenient().when(settingsRepository.findByUserId(any())).thenReturn(Optional.of(settings));
    }

    @Test
    @DisplayName("Price dropped — store a point and notify")
    void notifiesOnPriceDrop() {
        givenParsedPrice("90000.00");
        givenPreviousPrice("100000.00");
        givenNotificationSettings();

        priceCheckService.check(PRODUCT_ID, URL);

        verify(priceHistoryRepository).save(any(PriceHistory.class));

        ArgumentCaptor<PriceDropNotification> captor = ArgumentCaptor.forClass(PriceDropNotification.class);
        verify(notificationService).send(captor.capture(), any());

        PriceDropNotification notification = captor.getValue();
        assertThat(notification.oldPrice()).isEqualByComparingTo("100000.00");
        assertThat(notification.newPrice()).isEqualByComparingTo("90000.00");
        assertThat(notification.recipientEmail()).isEqualTo("user@example.com");
        assertThat(notification.percentDrop()).isEqualByComparingTo("10.0");

        assertThat(product.getLastNotifiedPrice()).isEqualByComparingTo("90000.00");
    }

    @Test
    @DisplayName("Price rose — write a point, but wake nobody")
    void storesButDoesNotNotifyOnPriceIncrease() {
        givenParsedPrice("110000.00");
        givenPreviousPrice("100000.00");

        priceCheckService.check(PRODUCT_ID, URL);

        verify(priceHistoryRepository).save(any(PriceHistory.class));
        verify(notificationService, never()).send(any(), any());
    }

    @Test
    @DisplayName("ON_CHANGE: the price did not change — no new history point appears")
    void onChangeSkipsUnchangedPrice() {
        givenParsedPrice("100000.00");
        givenPreviousPrice("100000.00");

        priceCheckService.check(PRODUCT_ID, URL);

        verify(priceHistoryRepository, never()).save(any());
        verify(notificationService, never()).send(any(), any());
        assertThat(product.getLastCheckedAt()).isNotNull();
    }

    @Test
    @DisplayName("ALWAYS: a point is written on every check, even if the price is the same")
    void alwaysStoresEveryCheck() {
        properties.getHistory().setStoreMode(StoreMode.ALWAYS);

        givenParsedPrice("100000.00");
        givenPreviousPrice("100000.00");

        priceCheckService.check(PRODUCT_ID, URL);

        verify(priceHistoryRepository).save(any(PriceHistory.class));
        verify(notificationService, never()).send(any(), any());
    }

    @Test
    @DisplayName("Threshold 10%: a 5% drop does not notify")
    void respectsPercentThreshold() {
        product.setThresholdType(ThresholdType.PERCENT);
        product.setThresholdValue(new BigDecimal("10"));

        givenParsedPrice("95000.00");
        givenPreviousPrice("100000.00");

        priceCheckService.check(PRODUCT_ID, URL);

        verify(priceHistoryRepository).save(any(PriceHistory.class));
        verify(notificationService, never()).send(any(), any());
    }

    @Test
    @DisplayName("Do not write about an already-notified price a second time")
    void doesNotNotifyTwiceAboutSamePrice() {
        product.setLastNotifiedPrice(new BigDecimal("90000.00"));

        givenParsedPrice("90000.00");
        givenPreviousPrice("100000.00");

        priceCheckService.check(PRODUCT_ID, URL);

        verify(notificationService, never()).send(any(), any());
    }

    @Test
    @DisplayName("The first check of a product sends no notification: there is nothing to compare against")
    void firstCheckDoesNotNotify() {
        givenParsedPrice("100000.00");
        when(priceHistoryRepository.findFirstByProductIdOrderByCheckedAtDesc(PRODUCT_ID))
                .thenReturn(Optional.empty());
        lenient().when(priceHistoryRepository.findMinPrice(PRODUCT_ID)).thenReturn(Optional.empty());

        priceCheckService.check(PRODUCT_ID, URL);

        verify(priceHistoryRepository).save(any(PriceHistory.class));
        verify(notificationService, never()).send(any(), any());
    }

    @Test
    @DisplayName("A parsing error propagates upward — the scheduler catches and logs it")
    void propagatesParsingFailure() {
        when(priceParsingService.parse(URL)).thenThrow(new PriceParsingException("Timeout"));

        assertThatThrownBy(() -> priceCheckService.check(PRODUCT_ID, URL))
                .isInstanceOf(PriceParsingException.class);

        verify(priceHistoryRepository, never()).save(any());
        verify(notificationService, never()).send(any(), any());
    }
}
