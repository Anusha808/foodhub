package com.foodhub.repository;

import com.foodhub.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository
        extends JpaRepository<Category, Long> {

    boolean existsByNameIgnoreCase(String name);
}