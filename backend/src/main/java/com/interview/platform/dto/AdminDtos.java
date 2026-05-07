package com.interview.platform.dto;

import java.util.List;
import java.util.Map;

public class AdminDtos {
    public static class UserModerationRequest {
        private Boolean enabled;
        private Boolean publicProfileVisible;
        private String reason;

        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
        public Boolean getPublicProfileVisible() { return publicProfileVisible; }
        public void setPublicProfileVisible(Boolean publicProfileVisible) { this.publicProfileVisible = publicProfileVisible; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    public static class InterviewerVerificationRequest {
        private Boolean verified;
        private String status;
        private String notes;
        private String reason;

        public Boolean getVerified() { return verified; }
        public void setVerified(Boolean verified) { this.verified = verified; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    public static class ReportModerationRequest {
        private String status;
        private String resolutionNotes;
        private String reason;

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getResolutionNotes() { return resolutionNotes; }
        public void setResolutionNotes(String resolutionNotes) { this.resolutionNotes = resolutionNotes; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    public static class ReviewModerationRequest {
        private Boolean visible;
        private String moderationNotes;
        private String reason;

        public Boolean getVisible() { return visible; }
        public void setVisible(Boolean visible) { this.visible = visible; }
        public String getModerationNotes() { return moderationNotes; }
        public void setModerationNotes(String moderationNotes) { this.moderationNotes = moderationNotes; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    public record TopicCount(String topic, long count) {}

    public record TrendPoint(String label, long value) {}

    public record RateTrendPoint(String label, double value) {}

    public record PlatformHealthIndicator(
            String key,
            String status,
            String detail,
            String updatedAt
    ) {}

    public record AdminUserItem(
            String id,
            String displayName,
            String email,
            List<String> roles,
            Boolean accountEnabled,
            Boolean publicProfileVisible,
            Boolean interviewerVerified,
            String verificationRequestStatus,
            String company,
            String currentRole,
            String createdAt,
            String lastLogin,
            Double trustScore,
            Boolean flagged,
            Long moderationActionCount
    ) {}

    public record AdminSessionItem(
            String id,
            String title,
            String status,
            String startTime,
            Integer durationMinutes,
            String meetingProvider,
            String interviewerId,
            String interviewerName,
            String candidateId,
            String candidateName,
            List<String> topics,
            Boolean cancellation,
            Boolean noShowRisk,
            Boolean disputeRisk,
            Long reportCount,
            Long openReportCount,
            String lastReportStatus
    ) {}

    public record AdminReportItem(
            String id,
            String category,
            String reason,
            String details,
            String status,
            Integer duplicateCount,
            String reporterId,
            String reporterName,
            String reportedUserId,
            String reportedUserName,
            String sessionId,
            String createdAt,
            String moderatedAt,
            String reviewedByAdminId,
            String resolutionNotes
    ) {}

    public record AdminAnalyticsResponse(
            int days,
            List<TrendPoint> userGrowthTrend,
            List<TrendPoint> activeUserTrend,
            List<TrendPoint> sessionTrend,
            List<TrendPoint> completedSessionTrend,
            List<TrendPoint> cancellationTrend,
            List<TrendPoint> reviewTrend,
            List<TrendPoint> reportTrend,
            List<RateTrendPoint> averageRatingTrend,
            List<RateTrendPoint> trustTrend,
            long activeUsers,
            long flaggedUsers,
            long disputedSessions,
            long noShowSessions
    ) {}

    public record ReviewTopicSummary(
            String topic,
            Integer rating,
            Map<String, Integer> skillRatings,
            String strengths,
            String improvementAreas,
            String comments
    ) {}

    public record ReviewQueueItem(
            String id,
            String reviewerName,
            String interviewerName,
            String sessionTitle,
            Integer rating,
            String comments,
            String createdAt,
            Boolean publicReview,
            Boolean flaggedForModeration,
            List<String> suspiciousFlags,
            Integer suspiciousScore,
            Double reviewQualityScore,
            String moderationNotes,
            Boolean interviewerVerified,
            Double interviewerReliability,
            Integer interviewerCancelledSessions,
            List<ReviewTopicSummary> topicSummaries
    ) {}

    public record ModerationAuditItem(
            String id,
            String entityType,
            String entityId,
            String action,
            String actorUserId,
            String subjectUserId,
            String reason,
            String summary,
            String createdAt
    ) {}

    public record AuditLogPage(
            List<ModerationAuditItem> items,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {}

    public record FlaggedUserItem(
            String userId,
            String displayName,
            String email,
            String verificationRequestStatus,
            Double trustScore,
            Double sessionCompletionRate,
            Double cancellationReliability,
            Double responseConsistencyScore,
            Double reviewQualityScore,
            List<String> indicators
    ) {}

    public record VerificationQueueItem(
            String userId,
            String displayName,
            String email,
            String linkedInUrl,
            String companyEmail,
            String status,
            String requestNotes,
            String adminNotes,
            String requestedAt,
            String reviewedAt
    ) {}

    public record TrustDashboardResponse(
            long flaggedReviewCount,
            long openReportCount,
            long pendingVerificationCount,
            long flaggedUserCount,
            double averageReviewQualityScore,
            double averageTrustScore,
            List<FlaggedUserItem> flaggedUsers,
            List<ReviewQueueItem> flaggedReviews,
            List<VerificationQueueItem> verificationQueue,
            List<ModerationAuditItem> recentModeration
    ) {}

    public record OverviewResponse(
            long totalUsers,
            long activeUsers,
            long totalInterviewers,
            long totalAdmins,
            long enabledUsers,
            long verifiedInterviewers,
            long flaggedUsers,
            long totalSessions,
            long completedSessions,
            long cancelledSessions,
            long pendingSessions,
            long disputedSessions,
            long noShowSessions,
            long openReports,
            long visiblePublicReviews,
            long hiddenPublicReviews,
            long flaggedReviews,
            long pendingVerificationRequests,
            double platformAverageRating,
            double completionRate,
            double cancellationRate,
            double averageReviewQualityScore,
            double averageTrustScore,
            List<TopicCount> topTopics,
            List<PlatformHealthIndicator> healthIndicators
    ) {}
}
