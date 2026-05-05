package com.interview.platform.service;

import com.interview.platform.model.Feedback;
import com.interview.platform.repository.FeedbackRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FeedbackService {

    @Autowired
    private FeedbackRepository feedbackRepository;

    public Feedback submitFeedback(Feedback feedback) {
        return feedbackRepository.save(feedback);
    }

    public List<Feedback> getFeedbackForSession(String sessionId) {
        return feedbackRepository.findBySessionId(sessionId);
    }
}
