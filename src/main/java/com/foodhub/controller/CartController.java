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

    @GetMapping("/cart")
    public String cart(
            HttpSession session,
            Model model) {

        Long userId =
                (Long) session.getAttribute("userId");

        if (userId == null) {
            return "redirect:/login";
        }

        List<CartItem> cartItems =
                cartItemRepository.findByUserId(userId);

        double cartTotal = 0;

        for (CartItem item : cartItems) {
            cartTotal += item.getTotalPrice();
        }

        int cartCount = 0;

        for (CartItem item : cartItems) {
            cartCount += item.getQuantity();
        }

        model.addAttribute("cartItems", cartItems);
        model.addAttribute("cartCount", cartCount);
        model.addAttribute("cartTotal", cartTotal);

        return "cart";
    }

    @PostMapping("/cart/add/{foodId}")
    public String addToCart(
            @PathVariable Long foodId,
            HttpSession session) {

        Long userId =
                (Long) session.getAttribute("userId");

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

        if (food.getStock() <= 0) {
            return "redirect:/foods";
        }

        CartItem cartItem =
                cartItemRepository
                        .findByUserIdAndFood(userId, food)
                        .orElse(null);

        if (cartItem != null) {

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
}