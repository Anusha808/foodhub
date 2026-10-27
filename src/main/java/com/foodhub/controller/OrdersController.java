
package com.foodhub.controller;

import com.foodhub.model.Order;
import com.foodhub.model.User;
import com.foodhub.repository.OrderRepository;
import com.foodhub.repository.UserRepository;

import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.List;

@Controller
public class OrdersController {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    public OrdersController(
            OrderRepository orderRepository,
            UserRepository userRepository) {

        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
    }

    // MY ORDERS

    @GetMapping("/orders")
    public String orders(
            HttpSession session,
            Model model) {

        Long userId = (Long) session.getAttribute("userId");

        if (userId == null) {
            return "redirect:/login";
        }

        User user = userRepository.findById(userId).orElse(null);

        if (user == null) {
            return "redirect:/login";
        }

        List<Order> orders =
                orderRepository.findByUserOrderByOrderDateDesc(user);

        model.addAttribute("user", user);
        model.addAttribute("orders", orders);

        return "orders";
    }

    // CANCEL ORDER

    @PostMapping("/orders/cancel/{id}")
    @Transactional
    public String cancelOrder(
            @PathVariable("id") Long orderId,
            HttpSession session) {

        Long userId = (Long) session.getAttribute("userId");

        if (userId == null) {
            return "redirect:/login";
        }

        User user = userRepository.findById(userId).orElse(null);

        if (user == null) {
            return "redirect:/login";
        }

        Order order = orderRepository.findById(orderId).orElse(null);

        if (order == null) {
            return "redirect:/orders";
        }

        // Verify that the order belongs to this user.
        if (order.getUser() == null
                || order.getUser().getId() == null
                || !order.getUser().getId().equals(userId)) {

            return "redirect:/orders";
        }

        // Do not cancel an already paid online order
        // through this simple cancellation endpoint.
        if ("ONLINE".equalsIgnoreCase(order.getPaymentMethod())
                && "PAID".equalsIgnoreCase(order.getPaymentStatus())) {

            return "redirect:/orders";
        }

        // Only eligible unpaid or COD orders can be cancelled.
        if (!"PLACED".equalsIgnoreCase(order.getStatus())
                && !"PENDING".equalsIgnoreCase(order.getStatus())) {

            return "redirect:/orders";
        }

        order.setStatus("CANCELLED");
        orderRepository.save(order);

        return "redirect:/orders";
    }
}
