package com.interview.platform.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Document(collection = "feedback")
@CompoundIndexes({
        @CompoundIndex(name = "reviewer_created_idx", def = "{'reviewerId': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "session_reviewer_idx", def = "{'sessionId': 1, 'reviewerId': 1}", unique = true),
        @CompoundIndex(name = "interviewer_public_created_idx", def = "{'interviewerId': 1, 'publicReview': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "moderation_created_idx", def = "{'flaggedForModeration': 1, 'createdAt': -1}")
})
public class Feedback {
    @Id
    private String id;
    @Indexed
    @NotBlank
    private String sessionId;
    @Indexed
    private String reviewerId;
    @Indexed
    private String targetUserId;
    @Indexed
    private String interviewerId;
    @Size(min = 12, max = 4000)
    private String comments;
    @Min(1)
    @Max(5)
    private int rating;
    private String strengths;
    private String weaknesses;
    private Integer communication = 0;
    private Integer technicalSkills = 0;
    private String ratingLevel;
    private String hiringRecommendation;
    private String communicationNotes;
    private String codingQualityNotes;
    private String problemSolvingNotes;
    private String finalSummary;
    private String privateNotes;
    private Boolean shareWithInterviewee = true;
    private Integer professionalism = 0;
    private Integer politeness = 0;
    private Integer punctuality = 0;
    private Integer clarity = 0;
    private Boolean inappropriateBehaviorReported = false;
    private String inappropriateBehaviorDetails;
    private String overallExperience;
    private String recommendations;
    private String improvementAreas;
    private String reviewType;
    private Boolean publicReview = false;
    @Indexed
    private Boolean flaggedForModeration = false;
    private List<String> suspiciousFlags = new ArrayList<>();
    private Integer suspiciousScore = 0;
    private Double reviewQualityScore = 0.0;
    private String contentFingerprint;
    private Instant reviewedWindowStartedAt;
    private String moderationNotes;
    private String moderatedByAdminId;
    private Instant moderatedAt;
    private List<TopicFeedback> topicFeedback = new ArrayList<>();
    private Instant createdAt;

    public Feedback() {}

    public Feedback(String sessionId, String reviewerId, String comments, int rating) {
        this.sessionId = sessionId;
        this.reviewerId = reviewerId;
        this.comments = comments;
        this.rating = rating;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getReviewerId() { return reviewerId; }
    public void setReviewerId(String reviewerId) { this.reviewerId = reviewerId; }
    public String getTargetUserId() { return targetUserId; }
    public void setTargetUserId(String targetUserId) { this.targetUserId = targetUserId; }
    public String getInterviewerId() { return interviewerId; }
    public void setInterviewerId(String interviewerId) { this.interviewerId = interviewerId; }
    public String getComments() { return comments; }
    public void setComments(String comments) { this.comments = comments; }
    public int getRating() { return rating; }
    public void setRating(int rating) { this.rating = rating; }
    public String getStrengths() { return strengths; }
    public void setStrengths(String strengths) { this.strengths = strengths; }
    public String getWeaknesses() { return weaknesses; }
    public void setWeaknesses(String weaknesses) { this.weaknesses = weaknesses; }
    public Integer getCommunication() { return communication; }
    public void setCommunication(Integer communication) { this.communication = communication == null ? 0 : communication; }
    public Integer getTechnicalSkills() { return technicalSkills; }
    public void setTechnicalSkills(Integer technicalSkills) { this.technicalSkills = technicalSkills == null ? 0 : technicalSkills; }
    public String getRatingLevel() { return ratingLevel; }
    public void setRatingLevel(String ratingLevel) { this.ratingLevel = ratingLevel; }
    public String getHiringRecommendation() { return hiringRecommendation; }
    public void setHiringRecommendation(String hiringRecommendation) { this.hiringRecommendation = hiringRecommendation; }
    public String getCommunicationNotes() { return communicationNotes; }
    public void setCommunicationNotes(String communicationNotes) { this.communicationNotes = communicationNotes; }
    public String getCodingQualityNotes() { return codingQualityNotes; }
    public void setCodingQualityNotes(String codingQualityNotes) { this.codingQualityNotes = codingQualityNotes; }
    public String getProblemSolvingNotes() { return problemSolvingNotes; }
    public void setProblemSolvingNotes(String problemSolvingNotes) { this.problemSolvingNotes = problemSolvingNotes; }
    public String getFinalSummary() { return finalSummary; }
    public void setFinalSummary(String finalSummary) { this.finalSummary = finalSummary; }
    public String getPrivateNotes() { return privateNotes; }
    public void setPrivateNotes(String privateNotes) { this.privateNotes = privateNotes; }
    public Boolean getShareWithInterviewee() { return shareWithInterviewee == null || shareWithInterviewee; }
    public void setShareWithInterviewee(Boolean shareWithInterviewee) { this.shareWithInterviewee = shareWithInterviewee == null || shareWithInterviewee; }
    public Integer getProfessionalism() { return professionalism == null ? 0 : professionalism; }
    public void setProfessionalism(Integer professionalism) { this.professionalism = professionalism == null ? 0 : professionalism; }
    public Integer getPoliteness() { return politeness == null ? 0 : politeness; }
    public void setPoliteness(Integer politeness) { this.politeness = politeness == null ? 0 : politeness; }
    public Integer getPunctuality() { return punctuality == null ? 0 : punctuality; }
    public void setPunctuality(Integer punctuality) { this.punctuality = punctuality == null ? 0 : punctuality; }
    public Integer getClarity() { return clarity == null ? 0 : clarity; }
    public void setClarity(Integer clarity) { this.clarity = clarity == null ? 0 : clarity; }
    public Boolean getInappropriateBehaviorReported() { return inappropriateBehaviorReported != null && inappropriateBehaviorReported; }
    public void setInappropriateBehaviorReported(Boolean inappropriateBehaviorReported) { this.inappropriateBehaviorReported = inappropriateBehaviorReported != null && inappropriateBehaviorReported; }
    public String getInappropriateBehaviorDetails() { return inappropriateBehaviorDetails; }
    public void setInappropriateBehaviorDetails(String inappropriateBehaviorDetails) { this.inappropriateBehaviorDetails = inappropriateBehaviorDetails; }
    public String getOverallExperience() { return overallExperience; }
    public void setOverallExperience(String overallExperience) { this.overallExperience = overallExperience; }
    public String getRecommendations() { return recommendations; }
    public void setRecommendations(String recommendations) { this.recommendations = recommendations; }
    public String getImprovementAreas() {
        if ((improvementAreas == null || improvementAreas.isBlank()) && recommendations != null && !recommendations.isBlank()) {
            return recommendations;
        }
        return improvementAreas;
    }
    public void setImprovementAreas(String improvementAreas) { this.improvementAreas = improvementAreas; }
    public String getReviewType() { return reviewType; }
    public void setReviewType(String reviewType) { this.reviewType = reviewType; }
    public Boolean getPublicReview() { return publicReview; }
    public void setPublicReview(Boolean publicReview) { this.publicReview = publicReview != null && publicReview; }
    public Boolean getFlaggedForModeration() { return flaggedForModeration; }
    public void setFlaggedForModeration(Boolean flaggedForModeration) { this.flaggedForModeration = flaggedForModeration != null && flaggedForModeration; }
    public List<String> getSuspiciousFlags() { return suspiciousFlags == null ? new ArrayList<>() : suspiciousFlags; }
    public void setSuspiciousFlags(List<String> suspiciousFlags) { this.suspiciousFlags = suspiciousFlags == null ? new ArrayList<>() : suspiciousFlags; }
    public Integer getSuspiciousScore() { return suspiciousScore == null ? 0 : suspiciousScore; }
    public void setSuspiciousScore(Integer suspiciousScore) { this.suspiciousScore = suspiciousScore == null ? 0 : suspiciousScore; }
    public Double getReviewQualityScore() { return reviewQualityScore == null ? 0.0 : reviewQualityScore; }
    public void setReviewQualityScore(Double reviewQualityScore) { this.reviewQualityScore = reviewQualityScore == null ? 0.0 : reviewQualityScore; }
    public String getContentFingerprint() { return contentFingerprint; }
    public void setContentFingerprint(String contentFingerprint) { this.contentFingerprint = contentFingerprint; }
    public Instant getReviewedWindowStartedAt() { return reviewedWindowStartedAt; }
    public void setReviewedWindowStartedAt(Instant reviewedWindowStartedAt) { this.reviewedWindowStartedAt = reviewedWindowStartedAt; }
    public String getModerationNotes() { return moderationNotes; }
    public void setModerationNotes(String moderationNotes) { this.moderationNotes = moderationNotes; }
    public String getModeratedByAdminId() { return moderatedByAdminId; }
    public void setModeratedByAdminId(String moderatedByAdminId) { this.moderatedByAdminId = moderatedByAdminId; }
    public Instant getModeratedAt() { return moderatedAt; }
    public void setModeratedAt(Instant moderatedAt) { this.moderatedAt = moderatedAt; }
    public List<TopicFeedback> getTopicFeedback() { return topicFeedback == null ? new ArrayList<>() : topicFeedback; }
    public void setTopicFeedback(List<TopicFeedback> topicFeedback) { this.topicFeedback = topicFeedback == null ? new ArrayList<>() : topicFeedback; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public static class TopicFeedback {
        private String topic;
        private Integer rating = 0;
        private Map<String, Integer> skillRatings = new HashMap<>();
        private String examples;
        private String strengths;
        private String weaknesses;
        private String improvementAreas;
        private String comments;

        public String getTopic() { return topic; }
        public void setTopic(String topic) { this.topic = topic; }
        public Integer getRating() { return rating; }
        public void setRating(Integer rating) { this.rating = rating == null ? 0 : rating; }
        public Map<String, Integer> getSkillRatings() { return skillRatings == null ? new HashMap<>() : skillRatings; }
        public void setSkillRatings(Map<String, Integer> skillRatings) { this.skillRatings = skillRatings == null ? new HashMap<>() : skillRatings; }
        public String getExamples() { return examples; }
        public void setExamples(String examples) { this.examples = examples; }
        public String getStrengths() { return strengths; }
        public void setStrengths(String strengths) { this.strengths = strengths; }
        public String getWeaknesses() { return weaknesses; }
        public void setWeaknesses(String weaknesses) { this.weaknesses = weaknesses; }
        public String getImprovementAreas() { return improvementAreas; }
        public void setImprovementAreas(String improvementAreas) { this.improvementAreas = improvementAreas; }
        public String getComments() { return comments; }
        public void setComments(String comments) { this.comments = comments; }
    }
}
