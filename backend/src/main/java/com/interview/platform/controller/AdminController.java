package com.interview.platform.controller;

import com.interview.platform.api.ApiResponse;
import com.interview.platform.dto.AdminDtos;
import com.interview.platform.dto.PageResponse;
import com.interview.platform.exception.UnauthorizedException;
import com.interview.platform.model.User;
import com.interview.platform.model.UserReport;
import com.interview.platform.security.UserPrincipal;
import com.interview.platform.service.AdminService;
import com.interview.platform.service.OperationalDiagnosticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    private final AdminService adminService;
    private final OperationalDiagnosticsService operationalDiagnosticsService;

    public AdminController(AdminService adminService, OperationalDiagnosticsService operationalDiagnosticsService) {
        this.adminService = adminService;
        this.operationalDiagnosticsService = operationalDiagnosticsService;
    }

    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<AdminDtos.OverviewResponse>> overview() {
        return ResponseEntity.ok(ApiResponse.success("Admin overview fetched", adminService.overview()));
    }

    @GetMapping("/analytics")
    public ResponseEntity<ApiResponse<AdminDtos.AdminAnalyticsResponse>> analytics(@RequestParam(required = false) Integer days) {
        return ResponseEntity.ok(ApiResponse.success("Admin analytics fetched", adminService.analytics(days)));
    }

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<PageResponse<AdminDtos.AdminUserItem>>> users(@RequestParam(required = false) String q,
                                                                                     @RequestParam(required = false) String role,
                                                                                     @RequestParam(required = false) Boolean enabled,
                                                                                     @RequestParam(required = false) String verification,
                                                                                     @RequestParam(required = false) Boolean flagged,
                                                                                     @RequestParam(required = false) String sortBy,
                                                                                     @RequestParam(required = false) String sortDir,
                                                                                     @RequestParam(required = false) Integer page,
                                                                                     @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(ApiResponse.success("Users fetched", adminService.users(q, role, enabled, verification, flagged, sortBy, sortDir, page, size)));
    }

    @GetMapping("/sessions")
    public ResponseEntity<ApiResponse<PageResponse<AdminDtos.AdminSessionItem>>> sessions(@RequestParam(required = false) String q,
                                                                                            @RequestParam(required = false) String status,
                                                                                            @RequestParam(required = false) Boolean cancellationOnly,
                                                                                            @RequestParam(required = false) Boolean noShowOnly,
                                                                                            @RequestParam(required = false) Boolean disputedOnly,
                                                                                            @RequestParam(required = false) String sortBy,
                                                                                            @RequestParam(required = false) String sortDir,
                                                                                            @RequestParam(required = false) Integer page,
                                                                                            @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(ApiResponse.success("Sessions fetched", adminService.sessions(q, status, cancellationOnly, noShowOnly, disputedOnly, sortBy, sortDir, page, size)));
    }

    @GetMapping("/reports")
    public ResponseEntity<ApiResponse<PageResponse<AdminDtos.AdminReportItem>>> reports(@RequestParam(required = false) String q,
                                                                                          @RequestParam(required = false) String status,
                                                                                          @RequestParam(required = false) String category,
                                                                                          @RequestParam(required = false) String sortBy,
                                                                                          @RequestParam(required = false) String sortDir,
                                                                                          @RequestParam(required = false) Integer page,
                                                                                          @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(ApiResponse.success("Reports fetched", adminService.reports(q, status, category, sortBy, sortDir, page, size)));
    }

    @GetMapping("/reviews")
    public ResponseEntity<ApiResponse<PageResponse<AdminDtos.ReviewQueueItem>>> reviews(@RequestParam(required = false) String q,
                                                                                          @RequestParam(required = false) Boolean visible,
                                                                                          @RequestParam(required = false) Integer minRating,
                                                                                          @RequestParam(required = false) Boolean flaggedOnly,
                                                                                          @RequestParam(required = false) String sortBy,
                                                                                          @RequestParam(required = false) String sortDir,
                                                                                          @RequestParam(required = false) Integer page,
                                                                                          @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(ApiResponse.success("Review moderation queue fetched", adminService.reviews(q, visible, minRating, flaggedOnly, sortBy, sortDir, page, size)));
    }

    @GetMapping("/trust-dashboard")
    public ResponseEntity<ApiResponse<AdminDtos.TrustDashboardResponse>> trustDashboard() {
        return ResponseEntity.ok(ApiResponse.success("Trust dashboard fetched", adminService.trustDashboard()));
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<ApiResponse<AdminDtos.AuditLogPage>> auditLogs(@RequestParam(required = false) Integer page,
                                                                         @RequestParam(required = false) Integer size,
                                                                         @RequestParam(required = false) String entityType,
                                                                         @RequestParam(required = false) String subjectUserId,
                                                                         @RequestParam(required = false) String actorUserId,
                                                                         @RequestParam(required = false) String q) {
        return ResponseEntity.ok(ApiResponse.success("Audit logs fetched", adminService.auditLogs(page, size, entityType, subjectUserId, actorUserId, q)));
    }

    @GetMapping("/system-diagnostics")
    public ResponseEntity<ApiResponse<AdminDtos.SystemDiagnosticsResponse>> systemDiagnostics() {
        return ResponseEntity.ok(ApiResponse.success("System diagnostics fetched", operationalDiagnosticsService.snapshot()));
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
