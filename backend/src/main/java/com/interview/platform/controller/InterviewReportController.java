package com.interview.platform.controller;

import com.interview.platform.api.ApiResponse;
import com.interview.platform.exception.UnauthorizedException;
import com.interview.platform.model.InterviewReport;
import com.interview.platform.model.User;
import com.interview.platform.security.UserPrincipal;
import com.interview.platform.service.InterviewReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
public class InterviewReportController {
    private final InterviewReportService interviewReportService;

    public InterviewReportController(InterviewReportService interviewReportService) {
        this.interviewReportService = interviewReportService;
    }

    @GetMapping("/session/{sessionId}")
    public ResponseEntity<ApiResponse<InterviewReport>> getForSession(@PathVariable String sessionId, Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Report fetched", interviewReportService.getForSession(sessionId, currentUser(authentication))));
    }

    private User currentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new UnauthorizedException("Authentication required");
        }
        return principal.getUser();
    }
}

