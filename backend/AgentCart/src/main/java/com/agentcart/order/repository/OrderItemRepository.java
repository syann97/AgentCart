package com.agentcart.order.repository;

import com.agentcart.order.domain.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    @Query("SELECT DISTINCT oi.product.id FROM OrderItem oi WHERE oi.order.member.id = :memberId AND oi.createdAt >= :since")
    List<Long> findProductIdsOrderedByMemberSince(@Param("memberId") Long memberId, @Param("since") LocalDateTime since);
}
