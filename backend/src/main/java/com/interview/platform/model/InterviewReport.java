package com.interview.platform.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Document(collection = "interview_reports")
@CompoundIndexes({
        @CompoundIndex(name = "report_session_idx", def = "{'sessionId': 1}", unique = true),
        @CompoundIndex(name = "report_participant_created_idx", def = "{'intervieweeId': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "report_interviewer_created_idx", def = "{'interviewerId': 1, 'createdAt': -1}")
})
public class InterviewReport {
    @Id
    private String id;

    @Indexed
    private String sessionId;

    @Indexed
    private String interviewerId;

    @Indexed
    private String intervieweeId;

    private String interviewerName;
    private String intervieweeName;

    private List<String> topics = new ArrayList<>();
    private String sessionStartTime;

    private Integer overallRating = 0;
    private String strengths;
    private String weaknesses;
    private String improvementRoadmap;
    private String interviewerComments;

    private List<TopicReport> topicReports = new ArrayList<>();

    private Instant createdAt;
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getInterviewerId() { return interviewerId; }
    public void setInterviewerId(String interviewerId) { this.interviewerId = interviewerId; }
    public String getIntervieweeId() { return intervieweeId; }
    public void setIntervieweeId(String intervieweeId) { this.intervieweeId = intervieweeId; }
    public String getInterviewerName() { return interviewerName; }
    public void setInterviewerName(String interviewerName) { this.interviewerName = interviewerName; }
    public String getIntervieweeName() { return intervieweeName; }
    public void setIntervieweeName(String intervieweeName) { this.intervieweeName = intervieweeName; }
    public List<String> getTopics() { return topics == null ? new ArrayList<>() : topics; }
    public void setTopics(List<String> topics) { this.topics = topics == null ? new ArrayList<>() : topics; }
    public String getSessionStartTime() { return sessionStartTime; }
    public void setSessionStartTime(String sessionStartTime) { this.sessionStartTime = sessionStartTime; }
    public Integer getOverallRating() { return overallRating == null ? 0 : overallRating; }
    public void setOverallRating(Integer overallRating) { this.overallRating = overallRating == null ? 0 : overallRating; }
    public String getStrengths() { return strengths; }
    public void setStrengths(String strengths) { this.strengths = strengths; }
    public String getWeaknesses() { return weaknesses; }
    public void setWeaknesses(String weaknesses) { this.weaknesses = weaknesses; }
    public String getImprovementRoadmap() { return improvementRoadmap; }
    public void setImprovementRoadmap(String improvementRoadmap) { this.improvementRoadmap = improvementRoadmap; }
    public String getInterviewerComments() { return interviewerComments; }
    public void setInterviewerComments(String interviewerComments) { this.interviewerComments = interviewerComments; }
    public List<TopicReport> getTopicReports() { return topicReports == null ? new ArrayList<>() : topicReports; }
    public void setTopicReports(List<TopicReport> topicReports) { this.topicReports = topicReports == null ? new ArrayList<>() : topicReports; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public static class TopicReport {
        private String topic;
        private Integer rating = 0;
        private String strengths;
        private String weaknesses;
        private String improvementAreas;
        private String comments;
        private Map<String, Integer> skillRatings = new HashMap<>();

        public String getTopic() { return topic; }
        public void setTopic(String topic) { this.topic = topic; }
        public Integer getRating() { return rating == null ? 0 : rating; }
        public void setRating(Integer rating) { this.rating = rating == null ? 0 : rating; }
        public String getStrengths() { return strengths; }
        public void setStrengths(String strengths) { this.strengths = strengths; }
        public String getWeaknesses() { return weaknesses; }
        public void setWeaknesses(String weaknesses) { this.weaknesses = weaknesses; }
        public String getImprovementAreas() { return improvementAreas; }
        public void setImprovementAreas(String improvementAreas) { this.improvementAreas = improvementAreas; }
        public String getComments() { return comments; }
        public void setComments(String comments) { this.comments = comments; }
        public Map<String, Integer> getSkillRatings() { return skillRatings == null ? new HashMap<>() : skillRatings; }
        public void setSkillRatings(Map<String, Integer> skillRatings) { this.skillRatings = skillRatings == null ? new HashMap<>() : skillRatings; }
    }
}

