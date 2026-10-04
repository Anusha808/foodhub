package com.foodhub.controller;

import com.foodhub.model.Food;
import com.foodhub.repository.FoodRepository;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class FoodController {

    private final FoodRepository foodRepository;

    public FoodController(FoodRepository foodRepository) {
        this.foodRepository = foodRepository;
    }

    @GetMapping("/foods")
    public String foods(Model model) {

        List<Food> foods = foodRepository.findAll();

        model.addAttribute("foods", foods);

        return "foods";
    }
}