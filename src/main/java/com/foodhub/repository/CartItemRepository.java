package com.foodhub.repository;

import com.foodhub.model.CartItem;
import com.foodhub.model.Food;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    List<CartItem> findByUserId(Long userId);

    Optional<CartItem> findByUserIdAndFood(Long userId, Food food);
}