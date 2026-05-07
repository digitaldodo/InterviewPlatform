package com.interview.platform.controller;

import com.interview.platform.api.ApiResponse;
import com.interview.platform.dto.MarketplaceDtos;
import com.interview.platform.exception.UnauthorizedException;
import com.interview.platform.security.UserPrincipal;
import com.interview.platform.service.PrepService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/prep")
public class PrepController {
    private final PrepService prepService;

    public PrepController(PrepService prepService) {
        this.prepService = prepService;
    }

    @GetMapping("/hub")
    public ResponseEntity<ApiResponse<MarketplaceDtos.PrepHubResponse>> hub(Authentication authentication) {
        String userId = currentUserId(authentication);
        MarketplaceDtos.PrepHubResponse response = prepService.buildHub(userId);
        return ResponseEntity.ok(ApiResponse.success("Prep hub fetched", response));
    }

    private String currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new UnauthorizedException("Authentication required");
        }
        return principal.getUser().getId();
    }
}
