
package com.foodhub.controller;

import com.foodhub.repository.UserRepository;
import jakarta.servlet.http.HttpSession;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin")
public class AdminUserController {

    private final UserRepository userRepository;

    public AdminUserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/users")
    public String manageUsers(HttpSession session, Model model) {

        if (session.getAttribute("adminId") == null
                || !"ADMIN".equalsIgnoreCase(
                String.valueOf(session.getAttribute("adminRole")))) {
            return "redirect:/admin/login";
        }

        model.addAttribute("adminName", session.getAttribute("adminName"));
        model.addAttribute("users", userRepository.findAll());

        return "admin-users";
    }
}
