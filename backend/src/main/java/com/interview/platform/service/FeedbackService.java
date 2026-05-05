package com.interview.platform.service;

import com.interview.platform.model.Feedback;
import com.interview.platform.repository.FeedbackRepository;
import com.interview.platform.repository.SessionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FeedbackService {

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private SessionRepository sessionRepository;

    public Feedback submitFeedback(Feedback feedback) {
        if (feedback != null) {
            if (feedback.getRating() < 1 || feedback.getRating() > 5) {
                throw new IllegalArgumentException("Rating must be between 1 and 5");
            }
            if (feedback.getSessionId() == null || !sessionRepository.existsById(feedback.getSessionId())) {
                throw new IllegalArgumentException("Invalid or non-existent session ID");
            }
        }
        return feedbackRepository.save(feedback);
    }

    public List<Feedback> getFeedbackForSession(String sessionId) {
        return feedbackRepository.findBySessionId(sessionId);
    }
}
