package com.foodhub.controller;

import com.foodhub.model.CartItem;
import com.foodhub.model.Food;
import com.foodhub.model.Order;
import com.foodhub.model.User;
import com.foodhub.repository.CartItemRepository;
import com.foodhub.repository.FoodRepository;
import com.foodhub.repository.OrderRepository;
import com.foodhub.repository.UserRepository;

import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.util.List;

@Controller
public class CheckoutController {

    private final CartItemRepository cartItemRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final FoodRepository foodRepository;

    public CheckoutController(
            CartItemRepository cartItemRepository,
            UserRepository userRepository,
            OrderRepository orderRepository,
            FoodRepository foodRepository) {

        this.cartItemRepository = cartItemRepository;
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.foodRepository = foodRepository;
    }


    // =========================================================
    // SHOW CHECKOUT PAGE
    // =========================================================

    @GetMapping("/checkout")
    public String checkout(
            HttpSession session,
            Model model) {

        // -----------------------------------------------------
        // Get logged-in user ID
        // -----------------------------------------------------

        Long userId =
                (Long) session.getAttribute("userId");

        // User must be logged in
        if (userId == null) {
            return "redirect:/login";
        }


        // -----------------------------------------------------
        // Find logged-in user
        // -----------------------------------------------------

        User user =
                userRepository.findById(userId)
                        .orElse(null);

        if (user == null) {
            return "redirect:/login";
        }


        // -----------------------------------------------------
        // Get cart items
        // -----------------------------------------------------

        List<CartItem> cartItems =
                cartItemRepository.findByUserId(userId);


        // -----------------------------------------------------
        // Empty cart cannot go to checkout
        // -----------------------------------------------------

        if (cartItems.isEmpty()) {
            return "redirect:/cart";
        }


        // -----------------------------------------------------
        // Calculate cart total and count
        // -----------------------------------------------------

        double cartTotal = 0;

        int cartCount = 0;

        for (CartItem item : cartItems) {

            cartTotal += item.getTotalPrice();

            cartCount += item.getQuantity();
        }


        // -----------------------------------------------------
        // Send data to checkout.html
        // -----------------------------------------------------

        model.addAttribute("user", user);

        model.addAttribute("cartItems", cartItems);

        model.addAttribute("cartCount", cartCount);

        model.addAttribute("cartTotal", cartTotal);


        return "checkout";
    }


    // =========================================================
    // PLACE ORDER
    // =========================================================

    @PostMapping("/checkout/place-order")
    @Transactional
    public String placeOrder(
            HttpSession session,

            @RequestParam("customerName")
            String customerName,

            @RequestParam("phone")
            String phone,

            @RequestParam("address")
            String address,

            @RequestParam("paymentMethod")
            String paymentMethod) {


        // -----------------------------------------------------
        // Check logged-in user
        // -----------------------------------------------------

        Long userId =
                (Long) session.getAttribute("userId");

        if (userId == null) {
            return "redirect:/login";
        }


        // -----------------------------------------------------
        // Find user
        // -----------------------------------------------------

        User user =
                userRepository.findById(userId)
                        .orElse(null);

        if (user == null) {
            return "redirect:/login";
        }


        // -----------------------------------------------------
        // Get cart items
        // -----------------------------------------------------

        List<CartItem> cartItems =
                cartItemRepository.findByUserId(userId);


        // -----------------------------------------------------
        // Cart must not be empty
        // -----------------------------------------------------

        if (cartItems.isEmpty()) {
            return "redirect:/cart";
        }


        // -----------------------------------------------------
        // Calculate total amount
        // -----------------------------------------------------

        double totalAmount = 0;

        for (CartItem item : cartItems) {

            totalAmount += item.getTotalPrice();
        }


        // -----------------------------------------------------
        // Check food stock
        // -----------------------------------------------------

        for (CartItem item : cartItems) {

            Food food = item.getFood();

            if (food.getStock() < item.getQuantity()) {

                return "redirect:/cart";
            }
        }


        // -----------------------------------------------------
        // Create new Order
        // -----------------------------------------------------

        Order order = new Order();

        order.setUser(user);

        order.setCustomerName(customerName);

        order.setPhone(phone);

        order.setAddress(address);

        order.setTotalAmount(totalAmount);

        order.setPaymentMethod(paymentMethod);


        // -----------------------------------------------------
        // Set order status
        // -----------------------------------------------------

        /*
         * COD:
         * Order is directly placed.
         *
         * ONLINE:
         * Online payment integration is not implemented yet,
         * so the order remains PENDING.
         */

        if ("COD".equalsIgnoreCase(paymentMethod)) {

            order.setStatus("PLACED");

        } else {

            order.setStatus("PENDING");
        }


        // -----------------------------------------------------
        // Set order date
        // -----------------------------------------------------

        order.setOrderDate(LocalDateTime.now());


        // -----------------------------------------------------
        // Save Order
        // -----------------------------------------------------

        Order savedOrder =
                orderRepository.save(order);


        // -----------------------------------------------------
        // Reduce Food Stock
        // -----------------------------------------------------

        for (CartItem item : cartItems) {

            Food food = item.getFood();

            int remainingStock =
                    food.getStock() - item.getQuantity();

            food.setStock(remainingStock);

            foodRepository.save(food);
        }


        // -----------------------------------------------------
        // Clear Cart
        // -----------------------------------------------------

        cartItemRepository.deleteAll(cartItems);


        // -----------------------------------------------------
        // Redirect to Order Success Page
        // -----------------------------------------------------

        return "redirect:/order-success/" + savedOrder.getId();
    }


    // =========================================================
    // ORDER SUCCESS PAGE
    // =========================================================

    @GetMapping("/order-success/{orderId}")
    public String orderSuccess(
            @PathVariable Long orderId,
            HttpSession session,
            Model model) {


        // -----------------------------------------------------
        // Check logged-in user
        // -----------------------------------------------------

        Long userId =
                (Long) session.getAttribute("userId");

        if (userId == null) {
            return "redirect:/login";
        }


        // -----------------------------------------------------
        // Find order
        // -----------------------------------------------------

        Order order =
                orderRepository.findById(orderId)
                        .orElse(null);


        // -----------------------------------------------------
        // Order not found
        // -----------------------------------------------------

        if (order == null) {
            return "redirect:/cart";
        }


        // -----------------------------------------------------
        // Security check
        // Make sure the order belongs to this user
        // -----------------------------------------------------

        if (order.getUser() == null ||
                !order.getUser().getId().equals(userId)) {

            return "redirect:/cart";
        }


        // -----------------------------------------------------
        // Send order to success page
        // -----------------------------------------------------

        model.addAttribute("order", order);


        return "order-success";
    }
}