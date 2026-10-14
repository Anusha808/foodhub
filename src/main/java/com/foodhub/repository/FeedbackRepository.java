package com.foodhub.repository;

import com.foodhub.model.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    // Get all feedback submitted by a particular user
    List<Feedback> findByUserIdOrderByCreatedAtDesc(Long userId);

    // Get a user's feedback by type
    List<Feedback> findByUserIdAndFeedbackTypeOrderByCreatedAtDesc(
            Long userId, String feedbackType);

    // Check feedback for a particular user's order
    List<Feedback> findByUserIdAndOrderIdAndFeedbackType(
            Long userId, Long orderId, String feedbackType);

    // Get feedback for a particular order
    List<Feedback> findByOrderIdAndFeedbackTypeOrderByCreatedAtDesc(
            Long orderId, String feedbackType);

    // Get all customer feedback for the admin dashboard
    List<Feedback> findAllByOrderByCreatedAtDesc();
}