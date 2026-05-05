package com.interview.platform.service;

import com.interview.platform.model.Feedback;
import com.interview.platform.repository.FeedbackRepository;
import com.interview.platform.repository.SessionRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final SessionRepository sessionRepository;

    public FeedbackService(FeedbackRepository feedbackRepository, SessionRepository sessionRepository) {
        this.feedbackRepository = feedbackRepository;
        this.sessionRepository = sessionRepository;
    }

    public Feedback submitFeedback(Feedback feedback) {
        if (feedback == null) {
            throw new IllegalArgumentException("Feedback details are required");
        }
        if (feedback.getRating() < 1 || feedback.getRating() > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5");
        }
        if (feedback.getReviewerId() == null || feedback.getReviewerId().isBlank()) {
            throw new IllegalArgumentException("Reviewer ID is required");
        }
        if (feedback.getComments() == null || feedback.getComments().isBlank()) {
            throw new IllegalArgumentException("Comments are required");
        }
        if (feedback.getSessionId() == null || !sessionRepository.existsById(feedback.getSessionId())) {
            throw new IllegalArgumentException("Invalid or non-existent session ID");
        }
        feedback.setComments(feedback.getComments().trim());
        return feedbackRepository.save(feedback);
    }

    public List<Feedback> getAllFeedback() {
        return feedbackRepository.findAll();
    }

    public List<Feedback> getFeedbackForSession(String sessionId) {
        return feedbackRepository.findBySessionId(sessionId);
    }
}
