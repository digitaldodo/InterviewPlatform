package com.interview.platform.controller;

import com.interview.platform.api.ApiResponse;
import com.interview.platform.exception.UnauthorizedException;
import com.interview.platform.model.Feedback;
import com.interview.platform.model.User;
import com.interview.platform.security.UserPrincipal;
import com.interview.platform.service.FeedbackService;
import org.springframework.security.core.Authentication;
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
    public ResponseEntity<ApiResponse<Feedback>> submitFeedback(@RequestBody Feedback feedback, Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Feedback submitted successfully", feedbackService.submitFeedback(currentUser(authentication), feedback)));
    }

    @GetMapping("/session/{sessionId}")
    public ResponseEntity<ApiResponse<List<Feedback>>> getFeedbackBySession(@PathVariable String sessionId) {
        return ResponseEntity.ok(ApiResponse.success("Feedback fetched successfully", feedbackService.getFeedbackForSession(sessionId)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Feedback>>> getAllFeedback() {
        return ResponseEntity.ok(ApiResponse.success("Feedback fetched successfully", feedbackService.getAllFeedback()));
    }

    @GetMapping("/interviewer/{interviewerId}/public")
    public ResponseEntity<ApiResponse<List<Feedback>>> publicReviews(@PathVariable String interviewerId) {
        return ResponseEntity.ok(ApiResponse.success("Public interviewer reviews fetched successfully",
                feedbackService.publicReviewsForInterviewer(interviewerId)));
    }

    private User currentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new UnauthorizedException("Authentication required");
        }
        return principal.getUser();
    }
}
