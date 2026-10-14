package com.foodhub.controller;

import com.foodhub.model.Category;
import com.foodhub.model.Food;
import com.foodhub.repository.CategoryRepository;
import com.foodhub.repository.FoodRepository;

import jakarta.servlet.http.HttpSession;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Controller
@RequestMapping("/admin")
public class AdminCategoryController {

    private final CategoryRepository categoryRepository;
    private final FoodRepository foodRepository;

    public AdminCategoryController(
            CategoryRepository categoryRepository,
            FoodRepository foodRepository) {
        this.categoryRepository = categoryRepository;
        this.foodRepository = foodRepository;
    }

    private boolean isAdmin(HttpSession session) {
        return session.getAttribute("adminId") != null
                && "ADMIN".equalsIgnoreCase(
                String.valueOf(session.getAttribute("adminRole")));
    }

    // Display category management page
    @GetMapping("/categories")
    public String showCategories(
            HttpSession session,
            Model model) {

        if (!isAdmin(session)) {
            return "redirect:/admin/login";
        }

        // Import categories already used by existing food items.
        List<String> existingNames = foodRepository.findAll()
                .stream()
                .map(Food::getCategory)
                .filter(name -> name != null && !name.isBlank())
                .map(String::trim)
                .distinct()
                .toList();

        for (String name : existingNames) {
            if (!categoryRepository.existsByNameIgnoreCase(name)) {
                categoryRepository.save(new Category(name));
            }
        }

        model.addAttribute("adminName", session.getAttribute("adminName"));
        model.addAttribute("categories", categoryRepository.findAll());
        model.addAttribute("category", new Category());

        return "admin-categories";
    }

    // Add a category
    @PostMapping("/categories/add")
    public String addCategory(
            @RequestParam("name") String name,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        if (!isAdmin(session)) {
            return "redirect:/admin/login";
        }

        if (name == null || name.isBlank()) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "Please enter a category name.");
            return "redirect:/admin/categories";
        }

        name = name.trim();

        if (categoryRepository.existsByNameIgnoreCase(name)) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "This category already exists.");
            return "redirect:/admin/categories";
        }

        categoryRepository.save(new Category(name));

        redirectAttributes.addFlashAttribute(
                "successMessage", "Category added successfully!");

        return "redirect:/admin/categories";
    }

    // Edit a category and update existing foods using that category
    @PostMapping("/categories/update/{id}")
    @Transactional
    public String updateCategory(
            @PathVariable Long id,
            @RequestParam("name") String name,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        if (!isAdmin(session)) {
            return "redirect:/admin/login";
        }

        if (name == null || name.isBlank()) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "Please enter a category name.");
            return "redirect:/admin/categories";
        }

        Category category = categoryRepository.findById(id).orElse(null);

        if (category == null) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "Category not found.");
            return "redirect:/admin/categories";
        }

        name = name.trim();
        final String newName = name;

        boolean duplicate = categoryRepository.findAll().stream()
                .anyMatch(other ->
                        !other.getId().equals(id)
                        && other.getName().equalsIgnoreCase(newName));

        if (duplicate) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "That category already exists.");
            return "redirect:/admin/categories";
        }

        String oldName = category.getName();

        List<Food> foodsToUpdate = foodRepository.findAll().stream()
                .filter(food -> food.getCategory() != null
                        && food.getCategory().equalsIgnoreCase(oldName))
                .toList();

        for (Food food : foodsToUpdate) {
            food.setCategory(newName);
        }

        foodRepository.saveAll(foodsToUpdate);

        category.setName(newName);
        categoryRepository.save(category);

        redirectAttributes.addFlashAttribute(
                "successMessage", "Category updated successfully!");

        return "redirect:/admin/categories";
    }

    // Delete a category only if no food item uses it
    @PostMapping("/categories/delete/{id}")
    public String deleteCategory(
            @PathVariable Long id,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        if (!isAdmin(session)) {
            return "redirect:/admin/login";
        }

        Category category = categoryRepository.findById(id).orElse(null);

        if (category == null) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "Category not found.");
            return "redirect:/admin/categories";
        }

        boolean inUse = foodRepository.findAll().stream()
                .anyMatch(food -> food.getCategory() != null
                        && food.getCategory().equalsIgnoreCase(
                                category.getName()));

        if (inUse) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "This category is used by food items. "
                    + "Change those food items' categories first.");
            return "redirect:/admin/categories";
        }

        categoryRepository.delete(category);

        redirectAttributes.addFlashAttribute(
                "successMessage", "Category deleted successfully!");

        return "redirect:/admin/categories";
    }
}