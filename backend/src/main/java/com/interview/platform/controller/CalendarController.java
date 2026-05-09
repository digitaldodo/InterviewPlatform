package com.interview.platform.controller;

import com.interview.platform.api.ApiResponse;
import com.interview.platform.dto.CalendarDtos;
import com.interview.platform.exception.UnauthorizedException;
import com.interview.platform.model.User;
import com.interview.platform.security.UserPrincipal;
import com.interview.platform.service.CalendarIntegrationService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/api/calendar")
public class CalendarController {
    private final CalendarIntegrationService calendarIntegrationService;

    public CalendarController(CalendarIntegrationService calendarIntegrationService) {
        this.calendarIntegrationService = calendarIntegrationService;
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<CalendarDtos.CalendarConnectionStatus>> status(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Calendar connection status fetched",
                calendarIntegrationService.googleStatus(currentUser(authentication))));
    }

    @PostMapping("/google/connect")
    public ResponseEntity<ApiResponse<CalendarDtos.CalendarConnectResponse>> connectGoogle(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Google authorization URL created",
                calendarIntegrationService.googleConnect(currentUser(authentication))));
    }

    @GetMapping("/google/callback")
    public void googleCallback(@RequestParam(required = false) String code,
                               @RequestParam(required = false) String state,
                               @RequestParam(required = false) String error,
                               HttpServletResponse response) throws IOException {
        response.sendRedirect(calendarIntegrationService.handleGoogleCallback(code, state, error));
    }

    @PostMapping("/google/sync")
    public ResponseEntity<ApiResponse<CalendarDtos.CalendarSyncResponse>> syncGoogle(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Google Calendar sync finished",
                calendarIntegrationService.syncUserSessions(currentUser(authentication))));
    }

    @DeleteMapping("/google")
    public ResponseEntity<ApiResponse<CalendarDtos.CalendarSyncResponse>> disconnectGoogle(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Google Calendar disconnected",
                calendarIntegrationService.disconnectGoogle(currentUser(authentication))));
    }

    private User currentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new UnauthorizedException("Authentication required");
        }
        return principal.getUser();
    }
}
