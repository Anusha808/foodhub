package com.foodhub.controller;

import com.foodhub.model.User;
import com.foodhub.repository.UserRepository;

import jakarta.servlet.http.HttpSession;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AdminController {

    private final UserRepository userRepository;

    public AdminController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // ADMIN LOGIN PAGE
    @GetMapping("/admin/login")
    public String adminLogin() {
        return "admin-login";
    }

    // PROCESS ADMIN LOGIN
    @PostMapping("/admin/login")
    public String processAdminLogin(
            @RequestParam("email") String email,
            @RequestParam("password") String password,
            HttpSession session,
            Model model) {

        User admin = userRepository.findByEmail(email).orElse(null);

        if (admin == null
                || admin.getPassword() == null
                || !password.equals(admin.getPassword())
                || !"ADMIN".equalsIgnoreCase(admin.getRole())) {

            model.addAttribute(
                    "error",
                    "Invalid admin email or password."
            );

            return "admin-login";
        }

        // Clear customer session
        session.removeAttribute("userId");
        session.removeAttribute("userName");
        session.removeAttribute("userEmail");
        session.removeAttribute("userRole");

        // Store admin session
        session.setAttribute("adminId", admin.getId());
        session.setAttribute("adminName", admin.getName());
        session.setAttribute("adminEmail", admin.getEmail());
        session.setAttribute("adminRole", admin.getRole());

        return "redirect:/admin/dashboard";
    }

    // ADMIN DASHBOARD
    @GetMapping("/admin/dashboard")
    public String adminDashboard(
            HttpSession session,
            Model model) {

        if (session.getAttribute("adminId") == null
                || !"ADMIN".equalsIgnoreCase(
                String.valueOf(session.getAttribute("adminRole")))) {

            return "redirect:/admin/login";
        }

        model.addAttribute(
                "adminName",
                session.getAttribute("adminName")
        );

        return "admin-dashboard";
    }

    // ADMIN LOGOUT
    @GetMapping("/admin/logout")
    public String adminLogout(HttpSession session) {
        session.invalidate();
        return "redirect:/admin/login?logout";
    }
}