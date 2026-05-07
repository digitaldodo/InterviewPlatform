package com.interview.platform.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Document(collection = "feedback")
public class Feedback {
    @Id
    private String id;
    @Indexed
    private String sessionId;
    @Indexed
    private String reviewerId;
    private String comments;
    private int rating;
    private String strengths;
    private String weaknesses;
    private Integer communication = 0;
    private Integer technicalSkills = 0;
    private String recommendations;
    private String improvementAreas;
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
    public String getRecommendations() { return recommendations; }
    public void setRecommendations(String recommendations) { this.recommendations = recommendations; }
    public String getImprovementAreas() {
        if ((improvementAreas == null || improvementAreas.isBlank()) && recommendations != null && !recommendations.isBlank()) {
            return recommendations;
        }
        return improvementAreas;
    }
    public void setImprovementAreas(String improvementAreas) { this.improvementAreas = improvementAreas; }
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
