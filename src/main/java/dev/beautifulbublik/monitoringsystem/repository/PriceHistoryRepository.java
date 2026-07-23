package dev.beautifulbublik.monitoringsystem.repository;

import dev.beautifulbublik.monitoringsystem.entity.PriceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PriceHistoryRepository extends JpaRepository<PriceHistory, Long> {

    List<PriceHistory> findByProductIdAndCheckedAtBetweenOrderByCheckedAtAsc(
            Long productId, Instant from, Instant to);

    Optional<PriceHistory> findFirstByProductIdOrderByCheckedAtDesc(Long productId);

    @Query("select min(h.price) from PriceHistory h where h.product.id = :productId")
    Optional<BigDecimal> findMinPrice(Long productId);

    void deleteByProductId(Long productId);
}
