package com.interview.platform.controller;

import com.interview.platform.api.ApiResponse;
import com.interview.platform.dto.FeedbackDtos;
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
    public ResponseEntity<ApiResponse<FeedbackDtos.FeedbackItem>> submitFeedback(@RequestBody Feedback feedback, Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Feedback submitted successfully", feedbackService.submitFeedback(currentUser(authentication), feedback)));
    }

    @GetMapping("/session/{sessionId}")
    public ResponseEntity<ApiResponse<List<FeedbackDtos.FeedbackItem>>> getFeedbackBySession(@PathVariable String sessionId,
                                                                                              Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Feedback fetched successfully",
                feedbackService.getFeedbackForSession(sessionId, currentUser(authentication))));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<FeedbackDtos.FeedbackItem>>> getAllFeedback(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Feedback fetched successfully",
                feedbackService.getFeedbackForUser(currentUser(authentication))));
    }

    @GetMapping("/interviewer/{interviewerId}/public")
    public ResponseEntity<ApiResponse<List<FeedbackDtos.PublicFeedbackItem>>> publicReviews(@PathVariable String interviewerId) {
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
