package com.neueda.app.repositories;

import com.neueda.app.enums.AssetClass;
import com.neueda.app.enums.OrderSide;
import com.neueda.app.enums.OrderStatus;
import com.neueda.app.enums.OrderType;
import com.neueda.app.models.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {
    List<Order> findByAccountIdOrderByCreatedOnDesc(String accountId);
    List<Order> findByStatusAndOrderType(OrderStatus status, OrderType orderType);
    List<Order> findByStatus(OrderStatus status);
    boolean existsByIdempotencyKey(String idempotencyKey);
    boolean existsByAccountIdAndStatus(String accountId, OrderStatus status);
    List<Order> findByStatusAndExpiresAtLessThanEqual(OrderStatus status, LocalDateTime time);

    /**
     * Cash held back by the account's open buys: the limit price, or the reference price for a
     * market order, times the quantity. Released when the order fills, is cancelled, rejected or expires.
     */
    @Query("""
        select coalesce(sum(o.price * o.quantity), 0) from Order o
        where o.accountId = :accountId
          and o.side = com.neueda.app.enums.OrderSide.BUY
          and o.status = com.neueda.app.enums.OrderStatus.PENDING
        """)
    BigDecimal lockedCash(@Param("accountId") String accountId);

    /** Shares of one symbol held back by the account's open sells. */
    @Query("""
        select coalesce(sum(o.quantity), 0) from Order o
        where o.accountId = :accountId and o.symbol = :symbol
          and o.side = com.neueda.app.enums.OrderSide.SELL
          and o.status = com.neueda.app.enums.OrderStatus.PENDING
        """)
    long lockedShares(@Param("accountId") String accountId, @Param("symbol") String symbol);

    /** Shares held back by the account's open sells, per symbol. */
    @Query("""
        select new com.neueda.app.repositories.LockedShares(o.symbol, sum(o.quantity)) from Order o
        where o.accountId = :accountId
          and o.side = com.neueda.app.enums.OrderSide.SELL
          and o.status = com.neueda.app.enums.OrderStatus.PENDING
        group by o.symbol
        """)
    List<LockedShares> lockedSharesBySymbol(@Param("accountId") String accountId);

    /**
     * Orders placed in [from, to) across all accounts, for the admin analytics. Each filter is
     * optional: null matches everything. Oldest first, so callers can group them in one pass.
     */
    @Query("""
        select new com.neueda.app.repositories.OrderActivity(
            i.symbol, i.name, i.assetClass, o.side, o.orderType, o.status,
            o.quantity, o.price, o.createdOn, a.ownerUsername)
        from Order o join o.instrument i join o.account a
        where o.createdOn >= :from and o.createdOn < :to
          and (:assetClass is null or i.assetClass = :assetClass)
          and (:side is null or o.side = :side)
          and (:orderType is null or o.orderType = :orderType)
          and (:trader is null or a.ownerUsername = :trader)
        order by o.createdOn
        """)
    List<OrderActivity> findActivity(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                     @Param("assetClass") AssetClass assetClass, @Param("side") OrderSide side,
                                     @Param("orderType") OrderType orderType, @Param("trader") String trader);

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
