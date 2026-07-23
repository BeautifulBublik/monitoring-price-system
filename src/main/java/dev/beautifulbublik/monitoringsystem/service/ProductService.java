package dev.beautifulbublik.monitoringsystem.service;

import dev.beautifulbublik.monitoringsystem.dto.CreateProductRequest;
import dev.beautifulbublik.monitoringsystem.dto.PricePointResponse;
import dev.beautifulbublik.monitoringsystem.dto.ProductDetailResponse;
import dev.beautifulbublik.monitoringsystem.dto.ProductResponse;
import dev.beautifulbublik.monitoringsystem.dto.UpdateProductRequest;
import dev.beautifulbublik.monitoringsystem.entity.PriceHistory;
import dev.beautifulbublik.monitoringsystem.entity.Product;
import dev.beautifulbublik.monitoringsystem.entity.ThresholdType;
import dev.beautifulbublik.monitoringsystem.entity.User;
import dev.beautifulbublik.monitoringsystem.exception.BadRequestException;
import dev.beautifulbublik.monitoringsystem.exception.ConflictException;
import dev.beautifulbublik.monitoringsystem.exception.ResourceNotFoundException;
import dev.beautifulbublik.monitoringsystem.parser.ParsedPrice;
import dev.beautifulbublik.monitoringsystem.parser.PriceParsingService;
import dev.beautifulbublik.monitoringsystem.parser.shop.ShopRuleRegistry;
import dev.beautifulbublik.monitoringsystem.repository.PriceHistoryRepository;
import dev.beautifulbublik.monitoringsystem.repository.ProductRepository;
import dev.beautifulbublik.monitoringsystem.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final UserRepository userRepository;
    private final PriceParsingService priceParsingService;
    private final ShopRuleRegistry shopRuleRegistry;
    private final TransactionTemplate transactionTemplate;


    /**
     * Adds a product and parses its price right away — it becomes the first history point.
     * <p>
     * If parsing fails, the product is <b>not</b> created and the client gets a 422:
     * silently creating a ticking but non-working tracker is the worst outcome — the user
     * would only find out about the problem after a week of silence.
     * <p>
     * The method is deliberately not annotated {@code @Transactional}: parsing is a network call
     * lasting seconds, and there is no point holding a DB connection during it. Writing both rows
     * (product + first history point) is done atomically via {@link TransactionTemplate}.
     * Moving it into a {@code @Transactional} method of this same class would be a mistake:
     * a call through {@code this} does not go through the proxy, so there would simply be no transaction.
     */
    public ProductResponse create(Long userId, CreateProductRequest request) {
        validateThreshold(request);

        String url = request.url().trim();
        if (productRepository.existsByUserIdAndUrl(userId, url)) {
            throw new ConflictException("This product is already being tracked");
        }

        String shopName = shopRuleRegistry.resolveShopName(url);
        ParsedPrice parsed = priceParsingService.parse(url);

        return transactionTemplate.execute(status -> persistNewProduct(userId, url, shopName, parsed, request));
    }

    private ProductResponse persistNewProduct(Long userId,
                                              String url,
                                              String shopName,
                                              ParsedPrice parsed,
                                              CreateProductRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User " + userId + " not found"));

        Product product = new Product(user, url, shopName);
        product.setTitle(parsed.title());
        product.setThresholdType(request.typeOrDefault());
        product.setThresholdBase(request.baseOrDefault());
        product.setThresholdValue(request.thresholdValue());
        product.setLastCheckedAt(Instant.now());

        Product saved = productRepository.save(product);
        priceHistoryRepository.save(
                new PriceHistory(saved, parsed.price(), parsed.currency(), saved.getLastCheckedAt()));

        log.info("User {} added product {} ('{}') from {}: starting price {} {}",
                userId, saved.getId(), saved.getTitle(), shopName, parsed.price(), parsed.currency());

        return ProductResponse.of(saved, parsed.price(), parsed.currency());
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> findAll(Long userId) {
        return productRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponseWithLatestPrice)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductDetailResponse findById(Long userId, Long productId) {
        Product product = requireOwned(userId, productId);

        List<PricePointResponse> history = priceHistoryRepository
                .findByProductIdAndCheckedAtBetweenOrderByCheckedAtAsc(productId, Instant.EPOCH, Instant.now())
                .stream()
                .map(PricePointResponse::of)
                .toList();

        BigDecimal minPrice = priceHistoryRepository.findMinPrice(productId).orElse(null);

        return new ProductDetailResponse(toResponseWithLatestPrice(product), minPrice, history);
    }

    @Transactional(readOnly = true)
    public List<PricePointResponse> findHistory(Long userId, Long productId, Instant from, Instant to) {
        requireOwned(userId, productId);

        Instant fromOrBeginning = from != null ? from : Instant.EPOCH;
        Instant toOrNow = to != null ? to : Instant.now();

        if (fromOrBeginning.isAfter(toOrNow)) {
            throw new BadRequestException("Parameter 'from' must be earlier than 'to'");
        }

        return priceHistoryRepository
                .findByProductIdAndCheckedAtBetweenOrderByCheckedAtAsc(productId, fromOrBeginning, toOrNow)
                .stream()
                .map(PricePointResponse::of)
                .toList();
    }

    @Transactional
    public ProductResponse update(Long userId, Long productId, UpdateProductRequest request) {
        Product product = requireOwned(userId, productId);

        if (request.trackingStatus() != null) {
            product.setTrackingStatus(request.trackingStatus());
        }
        if (request.thresholdType() != null) {
            product.setThresholdType(request.thresholdType());
        }
        if (request.thresholdBase() != null) {
            product.setThresholdBase(request.thresholdBase());
        }
        if (request.thresholdValue() != null) {
            product.setThresholdValue(request.thresholdValue());
        }

        if (product.getThresholdType() != ThresholdType.ANY_DROP && product.getThresholdValue() == null) {
            throw new BadRequestException(
                    "Threshold " + product.getThresholdType() + " requires thresholdValue to be set");
        }

        log.info("User {} updated product {}: status={}, threshold={} {}",
                userId, productId, product.getTrackingStatus(),
                product.getThresholdType(), product.getThresholdValue());

        return toResponseWithLatestPrice(product);
    }

    @Transactional
    public void delete(Long userId, Long productId) {
        Product product = requireOwned(userId, productId);

        priceHistoryRepository.deleteByProductId(productId);
        productRepository.delete(product);

        log.info("User {} deleted product {} along with its price history", userId, productId);
    }

    private Product requireOwned(Long userId, Long productId) {
        return productRepository.findByIdAndUserId(productId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Product " + productId + " not found"));
    }

    private ProductResponse toResponseWithLatestPrice(Product product) {
        Optional<PriceHistory> latest =
                priceHistoryRepository.findFirstByProductIdOrderByCheckedAtDesc(product.getId());
        return ProductResponse.of(
                product,
                latest.map(PriceHistory::getPrice).orElse(null),
                latest.map(PriceHistory::getCurrency).orElse(null));
    }

    private void validateThreshold(CreateProductRequest request) {
        if (request.requiresValue() && request.thresholdValue() == null) {
            throw new BadRequestException(
                    "Threshold " + request.typeOrDefault() + " requires thresholdValue to be set");
        }
    }
}
