package com.interview.platform.dto;

import java.util.List;
import java.util.Map;

public class MarketplaceDtos {
    public record SearchSuggestion(String label, String type) {}

    public record TopicReviewSummary(
            String topic,
            Integer rating,
            Map<String, Integer> skillRatings,
            String strengths,
            String improvementAreas,
            String comments
    ) {}

    public record InterviewerCard(
            String id,
            String username,
            String displayName,
            String avatarUrl,
            String company,
            String currentRole,
            String bio,
            String language,
            String timeZone,
            List<String> skills,
            List<String> interviewTopics,
            List<String> preferredDomains,
            List<Integer> sessionDurations,
            String experienceLevel,
            Integer yearsExperience,
            Double averageRating,
            Integer reviewCount,
            Integer completedInterviews,
            Integer completedSessions,
            Integer cancelledSessions,
            Double reliabilityScore,
            Boolean interviewerVerified,
            Boolean acceptingBookings,
            Boolean publicProfileVisible
    ) {}

    public record PublicReview(
            String id,
            String reviewerName,
            String reviewerAvatarUrl,
            int rating,
            String comments,
            String sessionTitle,
            String createdAt,
            List<TopicReviewSummary> topicSummaries
    ) {}

    public record PublicInterviewerProfile(
            String id,
            String username,
            String displayName,
            String avatarUrl,
            String company,
            String currentRole,
            String bio,
            String language,
            String timeZone,
            List<String> skills,
            List<String> interviewTopics,
            List<String> preferredDomains,
            List<Integer> sessionDurations,
            String experienceLevel,
            Integer yearsExperience,
            Double averageRating,
            Integer reviewCount,
            Integer completedInterviews,
            Integer completedSessions,
            Integer cancelledSessions,
            Double reliabilityScore,
            Boolean interviewerVerified,
            Boolean acceptingBookings,
            List<String> availabilityPreview,
            List<PublicReview> reviews
    ) {}

    public record PrepTrack(String title, String summary, List<String> focusAreas) {}

    public record PrepResource(String title, String type, String description, String actionLabel) {}

    public record PrepHubResponse(
            List<PrepTrack> companyTracks,
            List<PrepTrack> behavioralTracks,
            List<PrepTrack> codingTracks,
            List<PrepResource> resources
    ) {}
}
