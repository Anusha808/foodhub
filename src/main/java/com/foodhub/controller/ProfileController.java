package com.foodhub.controller;

import com.foodhub.model.User;
import com.foodhub.repository.UserRepository;

import jakarta.servlet.http.HttpSession;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ProfileController {

    private final UserRepository userRepository;

    public ProfileController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // ==============================
    // USER PROFILE
    // ==============================
    @GetMapping("/profile")
    public String profile(HttpSession session, Model model) {

        // Check whether user is logged in
        Long userId = (Long) session.getAttribute("userId");

        if (userId == null) {
            return "redirect:/login";
        }

        // Find logged-in user
        User user = userRepository.findById(userId).orElse(null);

        if (user == null) {
            session.invalidate();
            return "redirect:/login";
        }

        // Send user details to profile page
        model.addAttribute("user", user);

        return "profile";
    }
}