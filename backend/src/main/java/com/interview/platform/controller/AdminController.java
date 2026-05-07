package com.interview.platform.controller;

import com.interview.platform.api.ApiResponse;
import com.interview.platform.dto.AdminDtos;
import com.interview.platform.exception.UnauthorizedException;
import com.interview.platform.model.Session;
import com.interview.platform.model.User;
import com.interview.platform.model.UserReport;
import com.interview.platform.security.UserPrincipal;
import com.interview.platform.service.AdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<AdminDtos.OverviewResponse>> overview() {
        return ResponseEntity.ok(ApiResponse.success("Admin overview fetched", adminService.overview()));
    }

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<User>>> users(@RequestParam(required = false) String q,
                                                         @RequestParam(required = false) String role,
                                                         @RequestParam(required = false) Boolean enabled) {
        return ResponseEntity.ok(ApiResponse.success("Users fetched", adminService.users(q, role, enabled)));
    }

    @GetMapping("/interviewers")
    public ResponseEntity<ApiResponse<List<User>>> interviewers(@RequestParam(required = false) Boolean verified) {
        return ResponseEntity.ok(ApiResponse.success("Interviewers fetched", adminService.interviewers(verified)));
    }

    @GetMapping("/sessions")
    public ResponseEntity<ApiResponse<List<Session>>> sessions(@RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.success("Sessions fetched", adminService.sessions(status)));
    }

    @GetMapping("/reports")
    public ResponseEntity<ApiResponse<List<UserReport>>> reports(@RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.success("Reports fetched", adminService.reports(status)));
    }

    @GetMapping("/reviews")
    public ResponseEntity<ApiResponse<List<AdminDtos.ReviewQueueItem>>> reviews(@RequestParam(required = false) Boolean visible) {
        return ResponseEntity.ok(ApiResponse.success("Review moderation queue fetched", adminService.reviews(visible)));
    }

    @GetMapping("/trust-dashboard")
    public ResponseEntity<ApiResponse<AdminDtos.TrustDashboardResponse>> trustDashboard() {
        return ResponseEntity.ok(ApiResponse.success("Trust dashboard fetched", adminService.trustDashboard()));
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<ApiResponse<AdminDtos.AuditLogPage>> auditLogs(@RequestParam(required = false) Integer page,
                                                                         @RequestParam(required = false) Integer size,
                                                                         @RequestParam(required = false) String entityType,
                                                                         @RequestParam(required = false) String subjectUserId) {
        return ResponseEntity.ok(ApiResponse.success("Audit logs fetched", adminService.auditLogs(page, size, entityType, subjectUserId)));
    }

    @PatchMapping("/users/{userId}/moderation")
    public ResponseEntity<ApiResponse<User>> moderateUser(@PathVariable String userId,
                                                          @RequestBody AdminDtos.UserModerationRequest request,
                                                          Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("User moderation updated",
                adminService.updateUserModeration(userId, request, currentUser(authentication).getId())));
    }

    @PatchMapping("/interviewers/{userId}/verify")
    public ResponseEntity<ApiResponse<User>> verifyInterviewer(@PathVariable String userId,
                                                               @RequestBody AdminDtos.InterviewerVerificationRequest request,
                                                               Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Interviewer verification updated",
                adminService.verifyInterviewer(userId, request, currentUser(authentication).getId())));
    }

    @PatchMapping("/reports/{reportId}")
    public ResponseEntity<ApiResponse<UserReport>> moderateReport(@PathVariable String reportId,
                                                                  @RequestBody AdminDtos.ReportModerationRequest request,
                                                                  Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Report moderation updated",
                adminService.moderateReport(reportId, request, currentUser(authentication).getId())));
    }

    @PatchMapping("/reviews/{reviewId}")
    public ResponseEntity<ApiResponse<AdminDtos.ReviewQueueItem>> moderateReview(@PathVariable String reviewId,
                                                                                  @RequestBody AdminDtos.ReviewModerationRequest request,
                                                                                  Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Review moderation updated",
                adminService.moderateReview(reviewId, request, currentUser(authentication).getId())));
    }

    private User currentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new UnauthorizedException("Authentication required");
        }
        return principal.getUser();
    }
}
