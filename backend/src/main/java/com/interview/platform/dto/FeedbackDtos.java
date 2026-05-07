package com.interview.platform.dto;

import java.util.List;
import java.util.Map;

public class FeedbackDtos {
    public record TopicFeedbackSummary(
            String topic,
            Integer rating,
            Map<String, Integer> skillRatings,
            String strengths,
            String improvementAreas,
            String comments
    ) {}

    public record FeedbackItem(
            String id,
            String sessionId,
            String reviewerId,
            String interviewerId,
            int rating,
            String comments,
            String strengths,
            String weaknesses,
            Integer communication,
            Integer technicalSkills,
            String recommendations,
            String improvementAreas,
            String reviewType,
            Boolean publicReview,
            Boolean flaggedForModeration,
            List<String> suspiciousFlags,
            Integer suspiciousScore,
            Double reviewQualityScore,
            String createdAt,
            List<TopicFeedbackSummary> topicFeedback
    ) {}

    public record PublicFeedbackItem(
            String id,
            int rating,
            String comments,
            Double reviewQualityScore,
            String createdAt,
            List<TopicFeedbackSummary> topicFeedback
    ) {}
}
