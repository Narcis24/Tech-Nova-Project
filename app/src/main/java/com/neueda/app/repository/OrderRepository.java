package com.neueda.app.repository;

import com.neueda.app.model.Order;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository {
    Optional<Order> findById(UUID id);
    void save(Order order);
    void update(Order order);
    void delete(UUID id);
}
