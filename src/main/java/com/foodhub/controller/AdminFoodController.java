
package com.foodhub.controller;

import com.foodhub.model.Food;
import com.foodhub.repository.FoodRepository;

import jakarta.servlet.http.HttpSession;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin")
public class AdminFoodController {

    private final FoodRepository foodRepository;

    public AdminFoodController(FoodRepository foodRepository) {
        this.foodRepository = foodRepository;
    }

    // CHECK ADMIN LOGIN
    private boolean isAdmin(HttpSession session) {
        return session.getAttribute("adminId") != null
                && "ADMIN".equalsIgnoreCase(
                String.valueOf(session.getAttribute("adminRole")));
    }

    // LOAD FOOD MANAGEMENT PAGE
    @GetMapping("/foods")
    public String manageFoods(HttpSession session, Model model) {

        if (!isAdmin(session)) {
            return "redirect:/admin/login";
        }

        List<Food> foods = foodRepository.findAll();

        model.addAttribute("adminName", session.getAttribute("adminName"));
        model.addAttribute("foods", foods);

        // Build dropdown options from existing food categories.
        List<String> categories = foods.stream()
                .map(Food::getCategory)
                .filter(category -> category != null && !category.isBlank())
                .distinct()
                .sorted()
                .toList();

        model.addAttribute("categories", categories);

        if (!model.containsAttribute("food")) {
            model.addAttribute("food", new Food());
        }

        return "admin-foods";
    }

    // ADD FOOD ITEM
    @PostMapping("/foods/add")
    public String addFood(
            @ModelAttribute("food") Food food,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        if (!isAdmin(session)) {
            return "redirect:/admin/login";
        }

        if (food.getName() == null || food.getName().isBlank()
                || food.getCategory() == null
                || food.getCategory().isBlank()
                || food.getPrice() <= 0
                || food.getStock() < 0) {

            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Enter a food name, category, valid price and stock quantity.");

            return "redirect:/admin/foods";
        }

        try {
            food.setName(food.getName().trim());
            food.setCategory(food.getCategory().trim());

            foodRepository.save(food);

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Food item added successfully!");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Unable to add food item. Please check the entered details.");
        }

        return "redirect:/admin/foods";
    }

    // UPDATE FOOD ITEM
    @PostMapping("/foods/update/{id}")
    public String updateFood(
            @PathVariable("id") Long id,
            @ModelAttribute("food") Food updatedFood,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        if (!isAdmin(session)) {
            return "redirect:/admin/login";
        }

        Food existingFood = foodRepository.findById(id).orElse(null);

        if (existingFood == null) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "Food item not found.");
            return "redirect:/admin/foods";
        }

        if (updatedFood.getName() == null
                || updatedFood.getName().isBlank()
                || updatedFood.getCategory() == null
                || updatedFood.getCategory().isBlank()
                || updatedFood.getPrice() <= 0
                || updatedFood.getStock() < 0) {

            redirectAttributes.addFlashAttribute(
                    "errorMessage", "Please enter valid food details.");
            return "redirect:/admin/foods";
        }

        try {
            existingFood.setName(updatedFood.getName().trim());
            existingFood.setDescription(updatedFood.getDescription());
            existingFood.setPrice(updatedFood.getPrice());
            existingFood.setCategory(updatedFood.getCategory().trim());
            existingFood.setImage(updatedFood.getImage());
            existingFood.setStock(updatedFood.getStock());

            foodRepository.save(existingFood);

            redirectAttributes.addFlashAttribute(
                    "successMessage", "Food item updated successfully!");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Unable to update food item. Please try again.");
        }

        return "redirect:/admin/foods";
    }

    // DELETE FOOD ITEM
    @PostMapping("/foods/delete/{id}")
    public String deleteFood(
            @PathVariable("id") Long id,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        if (!isAdmin(session)) {
            return "redirect:/admin/login";
        }

        try {
            if (!foodRepository.existsById(id)) {
                redirectAttributes.addFlashAttribute(
                        "errorMessage", "Food item not found.");
                return "redirect:/admin/foods";
            }

            foodRepository.deleteById(id);

            redirectAttributes.addFlashAttribute(
                    "successMessage", "Food item deleted successfully!");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Unable to delete this food item. It may be linked to an existing order.");
        }

        return "redirect:/admin/foods";
    }
}
