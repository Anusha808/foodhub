package com.foodhub.controller;

import com.foodhub.repository.OrderRepository;
import jakarta.servlet.http.HttpSession;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin")
public class AdminOrderController {

    private final OrderRepository orderRepository;

    public AdminOrderController(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @GetMapping("/orders")
    public String manageOrders(HttpSession session, Model model) {

        if (session.getAttribute("adminId") == null
                || !"ADMIN".equalsIgnoreCase(
                String.valueOf(session.getAttribute("adminRole")))) {
            return "redirect:/admin/login";
        }

        model.addAttribute("adminName", session.getAttribute("adminName"));
        model.addAttribute("orders", orderRepository.findAll());

        return "admin-orders";
    }
}