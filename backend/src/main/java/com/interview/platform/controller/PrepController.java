package com.interview.platform.controller;

import com.interview.platform.api.ApiResponse;
import com.interview.platform.dto.MarketplaceDtos;
import com.interview.platform.dto.PrepDtos;
import com.interview.platform.exception.UnauthorizedException;
import com.interview.platform.security.UserPrincipal;
import com.interview.platform.service.PrepService;
import com.interview.platform.service.ResumeIntelligenceService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/prep")
public class PrepController {
    private final PrepService prepService;
    private final ResumeIntelligenceService resumeIntelligenceService;

    public PrepController(PrepService prepService, ResumeIntelligenceService resumeIntelligenceService) {
        this.prepService = prepService;
        this.resumeIntelligenceService = resumeIntelligenceService;
    }

    @GetMapping("/hub")
    public ResponseEntity<ApiResponse<MarketplaceDtos.PrepHubResponse>> hub(Authentication authentication) {
        String userId = currentUserId(authentication);
        MarketplaceDtos.PrepHubResponse response = prepService.buildHub(userId);
        return ResponseEntity.ok(ApiResponse.success("Prep hub fetched", response));
    }

    @GetMapping("/intelligence")
    public ResponseEntity<ApiResponse<PrepDtos.PrepIntelligenceDashboard>> intelligence(Authentication authentication) {
        String userId = currentUserId(authentication);
        PrepDtos.PrepIntelligenceDashboard response = resumeIntelligenceService.buildDashboard(userId);
        return ResponseEntity.ok(ApiResponse.success("Resume intelligence fetched", response));
    }

    @PostMapping("/resume/activate")
    public ResponseEntity<ApiResponse<PrepDtos.PrepIntelligenceDashboard>> activateResume(
            Authentication authentication,
            @RequestBody PrepDtos.ActivateResumeRequest request
    ) {
        String userId = currentUserId(authentication);
        PrepDtos.PrepIntelligenceDashboard response = resumeIntelligenceService.activateResumeVersion(userId, request == null ? null : request.getResumeVersionId());
        return ResponseEntity.ok(ApiResponse.success("Resume version activated", response));
    }

    @PostMapping("/jd/match")
    public ResponseEntity<ApiResponse<PrepDtos.JobMatchResult>> matchJobDescription(
            Authentication authentication,
            @RequestBody PrepDtos.JobDescriptionMatchRequest request
    ) {
        String userId = currentUserId(authentication);
        PrepDtos.JobMatchResult response = resumeIntelligenceService.matchJobDescription(
                userId,
                request == null ? null : request.getJobDescription(),
                request == null ? null : request.getRoleHint()
        );
        return ResponseEntity.ok(ApiResponse.success("Job description match generated", response));
    }

    @PostMapping(value = "/jd/match-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<PrepDtos.JobMatchResult>> matchJobDescriptionUpload(
            Authentication authentication,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "roleHint", required = false) String roleHint
    ) {
        String userId = currentUserId(authentication);
        PrepDtos.JobMatchResult response = resumeIntelligenceService.matchJobDescriptionUpload(userId, file, roleHint);
        return ResponseEntity.ok(ApiResponse.success("Job description upload matched", response));
    }

    private String currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new UnauthorizedException("Authentication required");
        }
        return principal.getUser().getId();
    }
}
