package com.interview.platform.dto;

import java.util.List;
import java.util.Map;

public class AdminDtos {
    public static class UserModerationRequest {
        private Boolean enabled;
        private Boolean publicProfileVisible;

        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
        public Boolean getPublicProfileVisible() { return publicProfileVisible; }
        public void setPublicProfileVisible(Boolean publicProfileVisible) { this.publicProfileVisible = publicProfileVisible; }
    }

    public static class InterviewerVerificationRequest {
        private Boolean verified;

        public Boolean getVerified() { return verified; }
        public void setVerified(Boolean verified) { this.verified = verified; }
    }

    public static class ReportModerationRequest {
        private String status;
        private String resolutionNotes;

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getResolutionNotes() { return resolutionNotes; }
        public void setResolutionNotes(String resolutionNotes) { this.resolutionNotes = resolutionNotes; }
    }

    public static class ReviewModerationRequest {
        private Boolean visible;
        private String moderationNotes;

        public Boolean getVisible() { return visible; }
        public void setVisible(Boolean visible) { this.visible = visible; }
        public String getModerationNotes() { return moderationNotes; }
        public void setModerationNotes(String moderationNotes) { this.moderationNotes = moderationNotes; }
    }

    public record TopicCount(String topic, long count) {}

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
            String moderationNotes,
            Boolean interviewerVerified,
            Double interviewerReliability,
            Integer interviewerCancelledSessions,
            List<ReviewTopicSummary> topicSummaries
    ) {}

    public record OverviewResponse(
            long totalUsers,
            long totalInterviewers,
            long totalAdmins,
            long enabledUsers,
            long verifiedInterviewers,
            long totalSessions,
            long completedSessions,
            long cancelledSessions,
            long pendingSessions,
            long openReports,
            long visiblePublicReviews,
            long hiddenPublicReviews,
            double platformAverageRating,
            double completionRate,
            double cancellationRate,
            List<TopicCount> topTopics
    ) {}
}
