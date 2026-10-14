
package com.foodhub.controller;

import com.foodhub.model.Feedback;
import com.foodhub.model.Order;
import com.foodhub.repository.FeedbackRepository;
import com.foodhub.repository.OrderRepository;

import jakarta.servlet.http.HttpSession;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
public class FeedbackController {

    private final FeedbackRepository feedbackRepository;
    private final OrderRepository orderRepository;

    public FeedbackController(
            FeedbackRepository feedbackRepository,
            OrderRepository orderRepository) {

        this.feedbackRepository = feedbackRepository;
        this.orderRepository = orderRepository;
    }

    private Long getLoggedInUserId(HttpSession session) {
        Object value = session.getAttribute("userId");

        if (value instanceof Number) {
            return ((Number) value).longValue();
        }

        return null;
    }

    // ORDER-SPECIFIC FEEDBACK PAGE
    @GetMapping("/feedback/order/{orderId}")
    public String orderFeedback(
            @PathVariable Long orderId,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes) {

        Long userId = getLoggedInUserId(session);

        if (userId == null) {
            return "redirect:/login";
        }

        Order order = orderRepository.findById(orderId).orElse(null);

        if (order == null) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "Order not found.");
            return "redirect:/orders";
        }

        List<Feedback> existingFeedback =
                feedbackRepository.findByUserIdAndOrderIdAndFeedbackType(
                        userId, orderId, "ORDER");

        model.addAttribute("order", order);
        model.addAttribute("existingFeedback", existingFeedback);
        model.addAttribute("orderId", orderId);

        return "order-feedback";
    }

    // GENERAL FOODHUB FEEDBACK PAGE
    @GetMapping("/feedback/general")
    public String generalFeedback(
            HttpSession session,
            Model model) {

        Long userId = getLoggedInUserId(session);

        if (userId == null) {
            return "redirect:/login";
        }

        List<Feedback> feedbackList =
                feedbackRepository
                        .findByUserIdAndFeedbackTypeOrderByCreatedAtDesc(
                                userId, "GENERAL");

        model.addAttribute("feedbackList", feedbackList);

        return "general-feedback";
    }

    // SAVE ORDER-SPECIFIC FEEDBACK
    @PostMapping("/feedback/order/submit")
    public String submitOrderFeedback(
            @RequestParam Long orderId,
            @RequestParam int rating,
            @RequestParam String comment,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        Long userId = getLoggedInUserId(session);

        if (userId == null) {
            return "redirect:/login";
        }

        Order order = orderRepository.findById(orderId).orElse(null);

        if (order == null) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "Order not found.");
            return "redirect:/orders";
        }

        if (rating < 1 || rating > 5) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "Please select a rating from 1 to 5.");
            return "redirect:/feedback/order/" + orderId;
        }

        if (comment == null || comment.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "Please write your feedback.");
            return "redirect:/feedback/order/" + orderId;
        }

        if (comment.trim().length() > 1000) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "Feedback must be within 1000 characters.");
            return "redirect:/feedback/order/" + orderId;
        }

        List<Feedback> existingFeedback =
                feedbackRepository.findByUserIdAndOrderIdAndFeedbackType(
                        userId, orderId, "ORDER");

        if (!existingFeedback.isEmpty()) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "You have already submitted feedback for this order.");
            return "redirect:/feedback/order/" + orderId;
        }

        Feedback feedback = new Feedback();
        feedback.setUserId(userId);
        feedback.setOrderId(orderId);
        feedback.setFeedbackType("ORDER");
        feedback.setRating(rating);
        feedback.setComment(comment.trim());

        feedbackRepository.save(feedback);

        redirectAttributes.addFlashAttribute(
                "successMessage", "Thank you! Your order feedback has been submitted.");

        return "redirect:/feedback/order/" + orderId;
    }

    // SAVE GENERAL FOODHUB FEEDBACK
    @PostMapping("/feedback/general/submit")
    public String submitGeneralFeedback(
            @RequestParam int rating,
            @RequestParam String comment,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        Long userId = getLoggedInUserId(session);

        if (userId == null) {
            return "redirect:/login";
        }

        if (rating < 1 || rating > 5) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "Please select a rating from 1 to 5.");
            return "redirect:/feedback/general";
        }

        if (comment == null || comment.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "Please write your feedback.");
            return "redirect:/feedback/general";
        }

        if (comment.trim().length() > 1000) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "Feedback must be within 1000 characters.");
            return "redirect:/feedback/general";
        }

        Feedback feedback = new Feedback();
        feedback.setUserId(userId);
        feedback.setFeedbackType("GENERAL");
        feedback.setOrderId(null);
        feedback.setRating(rating);
        feedback.setComment(comment.trim());

        feedbackRepository.save(feedback);

        redirectAttributes.addFlashAttribute(
                "successMessage", "Thank you for your feedback about FoodHub!");

        return "redirect:/feedback/general";
    }
}
