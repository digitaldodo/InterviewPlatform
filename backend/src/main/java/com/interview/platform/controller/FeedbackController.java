package com.interview.platform.controller;

import com.interview.platform.api.ApiResponse;
import com.interview.platform.model.Feedback;
import com.interview.platform.service.FeedbackService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Feedback>> submitFeedback(@RequestBody Feedback feedback) {
        return ResponseEntity.ok(ApiResponse.success("Feedback submitted successfully", feedbackService.submitFeedback(feedback)));
    }

    @GetMapping("/session/{sessionId}")
    public ResponseEntity<ApiResponse<List<Feedback>>> getFeedbackBySession(@PathVariable String sessionId) {
        return ResponseEntity.ok(ApiResponse.success("Feedback fetched successfully", feedbackService.getFeedbackForSession(sessionId)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Feedback>>> getAllFeedback() {
        return ResponseEntity.ok(ApiResponse.success("Feedback fetched successfully", feedbackService.getAllFeedback()));
    }
}
