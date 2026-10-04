package com.foodhub.repository;

import com.foodhub.model.Food;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FoodRepository extends JpaRepository<Food, Long> {

    List<Food> findByCategory(String category);

    List<Food> findByNameContainingIgnoreCase(String name);
}