package com.foodhub.controller;

import com.foodhub.model.Feedback;
import com.foodhub.model.User;
import com.foodhub.repository.FeedbackRepository;
import com.foodhub.repository.UserRepository;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class AdminFeedbackController {

    private final FeedbackRepository feedbackRepository;
    private final UserRepository userRepository;

    public AdminFeedbackController(
            FeedbackRepository feedbackRepository,
            UserRepository userRepository) {
        this.feedbackRepository = feedbackRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/admin/feedback")
    public String viewFeedback(Model model) {

        List<Feedback> feedbackList =
                feedbackRepository.findAllByOrderByCreatedAtDesc();

        Map<Long, String> customerNames = userRepository.findAll()
                .stream()
                .collect(Collectors.toMap(
                        User::getId,
                        user -> user.getName() != null
                                ? user.getName()
                                : "Customer",
                        (first, second) -> first
                ));

        long totalFeedback = feedbackList.size();

        long orderFeedback = feedbackList.stream()
                .filter(f -> "ORDER".equalsIgnoreCase(
                        f.getFeedbackType()))
                .count();

        long generalFeedback = feedbackList.stream()
                .filter(f -> "GENERAL".equalsIgnoreCase(
                        f.getFeedbackType()))
                .count();

        double averageRating = feedbackList.stream()
                .mapToInt(Feedback::getRating)
                .average()
                .orElse(0.0);

        model.addAttribute("feedbackList", feedbackList);
        model.addAttribute("customerNames", customerNames);
        model.addAttribute("totalFeedback", totalFeedback);
        model.addAttribute("orderFeedback", orderFeedback);
        model.addAttribute("generalFeedback", generalFeedback);
        model.addAttribute("averageRating", averageRating);

        return "admin-feedback";
    }
}