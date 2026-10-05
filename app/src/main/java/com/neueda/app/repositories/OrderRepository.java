package com.neueda.app.repositories;

import com.neueda.app.enums.OrderStatus;
import com.neueda.app.enums.OrderType;
import com.neueda.app.models.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {
    List<Order> findByAccountId(String accountId);
    List<Order> findByStatusAndOrderType(OrderStatus status, OrderType orderType);
    boolean existsByIdempotencyKey(String idempotencyKey);

    /**
     * Guarded PENDING -> FILLED transition. Returns the rows changed: 0 means the order
     * was no longer PENDING (already filled, cancelled or rejected), i.e. a duplicate.
     */
    @Modifying
    @Query("update Order o set o.status = com.neueda.app.enums.OrderStatus.FILLED, o.price = :price "
         + "where o.id = :id and o.status = com.neueda.app.enums.OrderStatus.PENDING")
    int fillIfPending(@Param("id") UUID id, @Param("price") BigDecimal price);
}
