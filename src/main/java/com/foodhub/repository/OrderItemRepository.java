
package com.foodhub.repository;

import com.foodhub.model.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository
        extends JpaRepository<OrderItem, Long> {
}
