package com.agentcart.product.repository;

import com.agentcart.product.domain.Product;
import com.agentcart.product.domain.ProductStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    Page<Product> findByStatus(ProductStatus status, Pageable pageable);
    boolean existsByNameAndBrand(String name, String brand);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT p FROM Product p WHERE " +
           "LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(p.brand) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(p.category) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<Product> search(@Param("keyword") String keyword, Pageable pageable);

    @Query(value = "SELECT id, MATCH(name, description, category, brand) " +
                   "AGAINST (:keyword IN BOOLEAN MODE) AS score " +
                   "FROM products " +
                   "WHERE MATCH(name, description, category, brand) AGAINST (:keyword IN BOOLEAN MODE) " +
                   "ORDER BY score DESC LIMIT :limit", nativeQuery = true)
    List<Object[]> bm25Search(@Param("keyword") String keyword, @Param("limit") int limit);

    @Query(value = "SELECT id, MATCH(name, description, category, brand) " +
                   "AGAINST (:keyword IN BOOLEAN MODE) AS score " +
                   "FROM products " +
                   "WHERE id IN (:allowedIds) " +
                   "AND MATCH(name, description, category, brand) AGAINST (:keyword IN BOOLEAN MODE) " +
                   "ORDER BY score DESC LIMIT :limit", nativeQuery = true)
    List<Object[]> bm25SearchWithinIds(@Param("keyword") String keyword,
                                       @Param("allowedIds") List<Long> allowedIds,
                                       @Param("limit") int limit);

    @Query("""
            SELECT p.id FROM Product p
            WHERE p.status = :status
              AND p.stock > 0
              AND (:minPrice IS NULL OR p.price >= :minPrice)
              AND (:maxPrice IS NULL OR p.price <= :maxPrice)
              AND NOT EXISTS (
                  SELECT oi.id FROM OrderItem oi
                  WHERE oi.product = p
                    AND oi.order.member.id = :memberId
                    AND oi.createdAt >= :since
              )
            """)
    List<Long> findEligibleProductIds(@Param("memberId") Long memberId,
                                      @Param("since") LocalDateTime since,
                                      @Param("status") ProductStatus status,
                                      @Param("minPrice") BigDecimal minPrice,
                                      @Param("maxPrice") BigDecimal maxPrice);

    @Query("""
            SELECT p.id FROM Product p
            WHERE p.status = :status
              AND p.stock > 0
              AND p.category IN :categories
              AND (:minPrice IS NULL OR p.price >= :minPrice)
              AND (:maxPrice IS NULL OR p.price <= :maxPrice)
              AND NOT EXISTS (
                  SELECT oi.id FROM OrderItem oi
                  WHERE oi.product = p
                    AND oi.order.member.id = :memberId
                    AND oi.createdAt >= :since
              )
            """)
    List<Long> findEligibleProductIdsByCategories(@Param("memberId") Long memberId,
                                                  @Param("since") LocalDateTime since,
                                                  @Param("status") ProductStatus status,
                                                  @Param("minPrice") BigDecimal minPrice,
                                                  @Param("maxPrice") BigDecimal maxPrice,
                                                  @Param("categories") List<String> categories);
}
