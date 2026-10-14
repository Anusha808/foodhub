
package com.foodhub.controller;

import com.foodhub.model.User;
import com.foodhub.repository.UserRepository;

import jakarta.servlet.http.HttpSession;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class HomeController {

    private final UserRepository userRepository;

    private final BCryptPasswordEncoder passwordEncoder =
            new BCryptPasswordEncoder();

    public HomeController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // HOME PAGE
    @GetMapping("/")
    public String home(HttpSession session, Model model) {
        model.addAttribute(
                "userName",
                session.getAttribute("userName")
        );
        return "home";
    }

    // CUSTOMER REGISTRATION PAGE
    @GetMapping("/register")
    public String register() {
        return "register";
    }

    // CUSTOMER LOGIN PAGE
    @GetMapping("/login")
    public String login() {
        return "login";
    }

    // REGISTER CUSTOMER
    @PostMapping("/register")
    public String registerUser(
            @ModelAttribute User user,
            @RequestParam(
                    name = "confirmPassword",
                    required = false
            ) String confirmPassword,
            @RequestParam(
                    name = "terms",
                    required = false
            ) String terms,
            Model model) {

        // Validate required fields
        if (user.getEmail() == null
                || user.getEmail().isBlank()
                || user.getPassword() == null
                || user.getPassword().isBlank()) {

            model.addAttribute("error", "Please complete all required fields.");
            return "register";
        }

        // Validate password confirmation
        if (confirmPassword == null
                || !user.getPassword().equals(confirmPassword)) {

            model.addAttribute("error", "Passwords do not match.");
            return "register";
        }

        // Validate terms acceptance
        if (terms == null) {
            model.addAttribute(
                    "error",
                    "Please accept the Terms & Conditions."
            );
            return "register";
        }

        // Check duplicate email
        if (userRepository.existsByEmail(user.getEmail())) {
            model.addAttribute(
                    "error",
                    "Email is already registered."
            );
            return "register";
        }

        // Create a customer account
        user.setRole("USER");
        user.setPassword(passwordEncoder.encode(user.getPassword()));

        userRepository.save(user);

        return "redirect:/login?registered";
    }

    // CUSTOMER LOGIN
    @PostMapping("/login")
    public String loginUser(
            @ModelAttribute User user,
            HttpSession session,
            Model model) {

        if (user.getEmail() == null
                || user.getPassword() == null) {

            model.addAttribute(
                    "error",
                    "Please enter your email and password."
            );
            return "login";
        }

        User existingUser = userRepository
                .findByEmail(user.getEmail())
                .orElse(null);

        // Validate customer credentials
        if (existingUser == null
                || existingUser.getPassword() == null
                || !passwordEncoder.matches(
                        user.getPassword(),
                        existingUser.getPassword())) {

            model.addAttribute(
                    "error",
                    "Invalid email or password."
            );
            return "login";
        }

        // Admins must use the admin login page
        if ("ADMIN".equalsIgnoreCase(existingUser.getRole())) {
            model.addAttribute(
                    "error",
                    "Please use the Admin Login page."
            );
            return "login";
        }

        // Store customer session
        session.setAttribute("userId", existingUser.getId());
        session.setAttribute("userName", existingUser.getName());
        session.setAttribute("userEmail", existingUser.getEmail());
        session.setAttribute("userRole", existingUser.getRole());

        return "redirect:/foods";
    }

    // CUSTOMER LOGOUT
    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/";
    }

    // TEMPORARY PASSWORD HASH GENERATOR
    // Remove this endpoint after updating the admin password.
    @GetMapping("/generate-password")
    @ResponseBody
    public String generatePassword() {
        return passwordEncoder.encode("admin123");
    }
}
