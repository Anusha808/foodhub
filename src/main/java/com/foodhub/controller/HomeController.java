package com.foodhub.controller;

import com.foodhub.model.User;
import com.foodhub.repository.UserRepository;

import jakarta.servlet.http.HttpSession;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class HomeController {

    // =====================================================
    // USER REPOSITORY
    // =====================================================

    private final UserRepository userRepository;


    // =====================================================
    // PASSWORD ENCODER
    // =====================================================

    private final BCryptPasswordEncoder passwordEncoder =
            new BCryptPasswordEncoder();


    // =====================================================
    // CONSTRUCTOR
    // =====================================================

    public HomeController(UserRepository userRepository) {

        this.userRepository = userRepository;
    }


    // =====================================================
    // HOME PAGE
    // =====================================================

    @GetMapping("/")
    public String home(
            HttpSession session,
            Model model) {

        // Get logged-in user's name from session
        String userName =
                (String) session.getAttribute("userName");

        // Send username to home.html
        model.addAttribute(
                "userName",
                userName
        );

        return "home";
    }


    // =====================================================
    // REGISTER PAGE
    // =====================================================

    @GetMapping("/register")
    public String register() {

        return "register";
    }


    // =====================================================
    // LOGIN PAGE
    // =====================================================

    @GetMapping("/login")
    public String login() {

        return "login";
    }


    // =====================================================
    // REGISTER USER
    // =====================================================

    @PostMapping("/register")
    @ResponseBody
    public String registerUser(
            @RequestBody User user) {

        // Check whether email already exists
        if (userRepository.existsByEmail(user.getEmail())) {

            return "Email is already registered.";
        }

        // Set default role
        user.setRole("USER");

        // Encrypt password
        String encryptedPassword =
                passwordEncoder.encode(
                        user.getPassword()
                );

        // Store encrypted password
        user.setPassword(encryptedPassword);

        // Save user
        userRepository.save(user);

        return "Registration successful!";
    }


    // =====================================================
    // LOGIN USER
    // =====================================================

    @PostMapping("/login")
    @ResponseBody
    public String loginUser(
            @RequestBody User user,
            HttpSession session) {

        // Find user using email
        User existingUser =
                userRepository
                        .findByEmail(user.getEmail())
                        .orElse(null);


        // User not found
        if (existingUser == null) {

            return "Invalid email or password.";
        }


        // Check password
        boolean passwordMatches =
                passwordEncoder.matches(
                        user.getPassword(),
                        existingUser.getPassword()
                );


        // Password incorrect
        if (!passwordMatches) {

            return "Invalid email or password.";
        }


        // =================================================
        // LOGIN SUCCESSFUL
        // SAVE USER DATA IN SESSION
        // =================================================

        session.setAttribute(
                "userId",
                existingUser.getId()
        );

        session.setAttribute(
                "userName",
                existingUser.getName()
        );

        session.setAttribute(
                "userEmail",
                existingUser.getEmail()
        );

        session.setAttribute(
                "userRole",
                existingUser.getRole()
        );


        return "Login successful!";
    }


    // =====================================================
    // LOGOUT
    // =====================================================

    @GetMapping("/logout")
    public String logout(
            HttpSession session) {

        // Destroy session
        session.invalidate();

        // Return to home page
        return "redirect:/";
    }
}