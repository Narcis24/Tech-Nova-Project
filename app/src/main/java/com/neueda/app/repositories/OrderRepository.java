package com.neueda.app.repositories;

import com.neueda.app.models.Order;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository {
    Optional<Order> findById(UUID id);
    void save(Order order);
    void update(Order order);
    void delete(UUID id);
}
