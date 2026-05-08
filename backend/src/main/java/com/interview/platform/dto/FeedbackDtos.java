package com.interview.platform.dto;

import java.util.List;
import java.util.Map;

public class FeedbackDtos {
    public record TopicFeedbackSummary(
            String topic,
            Integer rating,
            Map<String, Integer> skillRatings,
            String examples,
            String strengths,
            String weaknesses,
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
            String ratingLevel,
            String hiringRecommendation,
            String communicationNotes,
            String codingQualityNotes,
            String problemSolvingNotes,
            String finalSummary,
            String privateNotes,
            Boolean shareWithInterviewee,
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

    public record FeedbackDraftRequest(
            Integer rating,
            Integer communication,
            Integer technicalSkills,
            String ratingLevel,
            String strengths,
            String weaknesses,
            String hiringRecommendation,
            String communicationNotes,
            String codingQualityNotes,
            String problemSolvingNotes,
            String finalSummary,
            String shareableFeedback,
            String privateNotes,
            Boolean shareWithInterviewee,
            List<TopicFeedbackSummary> topicFeedback
    ) {}

    public record FeedbackDraftItem(
            String id,
            String sessionId,
            String interviewerId,
            Integer rating,
            Integer communication,
            Integer technicalSkills,
            String ratingLevel,
            String strengths,
            String weaknesses,
            String hiringRecommendation,
            String communicationNotes,
            String codingQualityNotes,
            String problemSolvingNotes,
            String finalSummary,
            String shareableFeedback,
            String privateNotes,
            Boolean shareWithInterviewee,
            String createdAt,
            String updatedAt,
            List<TopicFeedbackSummary> topicFeedback,
            Boolean submitted
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
