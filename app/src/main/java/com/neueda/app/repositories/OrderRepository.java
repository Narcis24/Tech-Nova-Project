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
     * Guarded status transition. Returns the rows changed: 0 means the order was not in
     * the {@code from} status, e.g. a fill that was already applied.
     */
    @Modifying
    @Query("update Order o set o.status = :to, o.price = :price, o.version = o.version + 1 where o.id = :id and o.status = :from")
    int transition(@Param("id") UUID id, @Param("from") OrderStatus from,
                   @Param("to") OrderStatus to, @Param("price") BigDecimal price);

    /** Guarded rejection with a reason, same 0-rows-means-not-in-{@code from} contract as transition. */
    @Modifying
    @Query("update Order o set o.status = :to, o.rejectionReason = :reason, o.version = o.version + 1 where o.id = :id and o.status = :from")
    int reject(@Param("id") UUID id, @Param("from") OrderStatus from,
               @Param("to") OrderStatus to, @Param("reason") String reason);

    /**
     * Guarded status change, same 0-rows-means-not-in-{@code from} contract as transition.
     * Clears the persistence context so a following findById sees the new status.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Order o set o.status = :to, o.version = o.version + 1 where o.id = :id and o.status = :from")
    int changeStatus(@Param("id") UUID id, @Param("from") OrderStatus from, @Param("to") OrderStatus to);
}
