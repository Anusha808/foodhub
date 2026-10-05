package com.foodhub.controller;

import com.foodhub.model.CartItem;
import com.foodhub.model.Food;
import com.foodhub.model.User;
import com.foodhub.repository.CartItemRepository;
import com.foodhub.repository.FoodRepository;
import com.foodhub.repository.UserRepository;

import jakarta.servlet.http.HttpSession;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.List;

@Controller
public class CartController {

    private final CartItemRepository cartItemRepository;
    private final FoodRepository foodRepository;
    private final UserRepository userRepository;

    public CartController(
            CartItemRepository cartItemRepository,
            FoodRepository foodRepository,
            UserRepository userRepository) {

        this.cartItemRepository = cartItemRepository;
        this.foodRepository = foodRepository;
        this.userRepository = userRepository;
    }

    // ================= CART PAGE =================

    @GetMapping("/cart")
    public String cart(
            HttpSession session,
            Model model) {

        Long userId =
                (Long) session.getAttribute("userId");

        // User must be logged in
        if (userId == null) {
            return "redirect:/login";
        }

        List<CartItem> cartItems =
                cartItemRepository.findByUserId(userId);

        double cartTotal = 0;

        int cartCount = 0;

        for (CartItem item : cartItems) {

            cartTotal += item.getTotalPrice();

            cartCount += item.getQuantity();
        }

        model.addAttribute("cartItems", cartItems);

        model.addAttribute("cartCount", cartCount);

        model.addAttribute("cartTotal", cartTotal);

        return "cart";
    }


    // ================= ADD TO CART =================

    @PostMapping("/cart/add/{foodId}")
    public String addToCart(
            @PathVariable Long foodId,
            HttpSession session) {

        Long userId =
                (Long) session.getAttribute("userId");

        // User must be logged in
        if (userId == null) {
            return "redirect:/login";
        }

        User user =
                userRepository.findById(userId)
                        .orElse(null);

        Food food =
                foodRepository.findById(foodId)
                        .orElse(null);

        if (user == null || food == null) {
            return "redirect:/foods";
        }

        // Check stock
        if (food.getStock() <= 0) {
            return "redirect:/foods";
        }

        CartItem cartItem =
                cartItemRepository
                        .findByUserIdAndFood(userId, food)
                        .orElse(null);

        if (cartItem != null) {

            // Increase quantity only if stock is available
            if (cartItem.getQuantity() < food.getStock()) {

                cartItem.setQuantity(
                        cartItem.getQuantity() + 1
                );

                cartItemRepository.save(cartItem);
            }

        } else {

            CartItem newCartItem =
                    new CartItem(user, food, 1);

            cartItemRepository.save(newCartItem);
        }

        return "redirect:/cart";
    }


    // ================= INCREASE QUANTITY =================

    @PostMapping("/cart/increase/{id}")
    public String increaseQuantity(
            @PathVariable Long id,
            HttpSession session) {

        Long userId =
                (Long) session.getAttribute("userId");

        if (userId == null) {
            return "redirect:/login";
        }

        CartItem cartItem =
                cartItemRepository.findById(id)
                        .orElse(null);

        if (cartItem == null) {
            return "redirect:/cart";
        }

        // Security check
        if (!cartItem.getUser().getId().equals(userId)) {
            return "redirect:/cart";
        }

        Food food = cartItem.getFood();

        // Check stock before increasing
        if (cartItem.getQuantity() < food.getStock()) {

            cartItem.setQuantity(
                    cartItem.getQuantity() + 1
            );

            cartItemRepository.save(cartItem);
        }

        return "redirect:/cart";
    }


    // ================= DECREASE QUANTITY =================

    @PostMapping("/cart/decrease/{id}")
    public String decreaseQuantity(
            @PathVariable Long id,
            HttpSession session) {

        Long userId =
                (Long) session.getAttribute("userId");

        if (userId == null) {
            return "redirect:/login";
        }

        CartItem cartItem =
                cartItemRepository.findById(id)
                        .orElse(null);

        if (cartItem == null) {
            return "redirect:/cart";
        }

        // Security check
        if (!cartItem.getUser().getId().equals(userId)) {
            return "redirect:/cart";
        }

        if (cartItem.getQuantity() > 1) {

            cartItem.setQuantity(
                    cartItem.getQuantity() - 1
            );

            cartItemRepository.save(cartItem);

        } else {

            cartItemRepository.delete(cartItem);
        }

        return "redirect:/cart";
    }


    // ================= REMOVE ITEM =================

    @PostMapping("/cart/remove/{id}")
    public String removeFromCart(
            @PathVariable Long id,
            HttpSession session) {

        Long userId =
                (Long) session.getAttribute("userId");

        if (userId == null) {
            return "redirect:/login";
        }

        CartItem cartItem =
                cartItemRepository.findById(id)
                        .orElse(null);

        if (cartItem == null) {
            return "redirect:/cart";
        }

        // Security check
        if (!cartItem.getUser().getId().equals(userId)) {
            return "redirect:/cart";
        }

        cartItemRepository.delete(cartItem);

        return "redirect:/cart";
    }
}