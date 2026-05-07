package com.interview.platform.controller;

import com.interview.platform.api.ApiResponse;
import com.interview.platform.dto.ReportDtos;
import com.interview.platform.dto.UserDtos;
import com.interview.platform.exception.UnauthorizedException;
import com.interview.platform.model.User;
import com.interview.platform.model.UserReport;
import com.interview.platform.security.UserPrincipal;
import com.interview.platform.service.TrustService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trust")
public class TrustController {
    private final TrustService trustService;

    public TrustController(TrustService trustService) {
        this.trustService = trustService;
    }

    @PostMapping("/reports")
    public ResponseEntity<ApiResponse<UserReport>> createReport(@RequestBody ReportDtos.CreateReportRequest request,
                                                                Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Report submitted successfully",
                trustService.submitReport(currentUser(authentication), request)));
    }

    @PostMapping("/verification-request")
    public ResponseEntity<ApiResponse<User>> submitVerificationRequest(@RequestBody UserDtos.VerificationRequestSubmission request,
                                                                       Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Verification request submitted successfully",
                trustService.submitVerificationRequest(currentUser(authentication), request)));
    }

    private User currentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new UnauthorizedException("Authentication required");
        }
        return principal.getUser();
    }
}
