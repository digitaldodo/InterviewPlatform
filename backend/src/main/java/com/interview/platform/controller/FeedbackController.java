package com.interview.platform.controller;

import com.interview.platform.model.Feedback;
import com.interview.platform.service.FeedbackService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/feedback")
public class FeedbackController {

    @Autowired
    private FeedbackService feedbackService;

    @PostMapping
    public ResponseEntity<Feedback> submitFeedback(@RequestBody Feedback feedback) {
        try {
            return ResponseEntity.ok(feedbackService.submitFeedback(feedback));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/session/{sessionId}")
    public ResponseEntity<List<Feedback>> getFeedbackBySession(@PathVariable String sessionId) {
        return ResponseEntity.ok(feedbackService.getFeedbackForSession(sessionId));
    }
}
