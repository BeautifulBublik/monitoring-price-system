package dev.beautifulbublik.monitoringsystem.repository;

import dev.beautifulbublik.monitoringsystem.entity.Product;
import dev.beautifulbublik.monitoringsystem.entity.TrackingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<Product> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndUrl(Long userId, String url);

    @Query("select p from Product p where p.trackingStatus = :status order by p.id")
    List<Product> findAllForCheck(TrackingStatus status);
}
