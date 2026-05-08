package com.interview.platform.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "feedback_drafts")
@CompoundIndexes({
        @CompoundIndex(name = "draft_session_interviewer_idx", def = "{'sessionId': 1, 'interviewerId': 1}", unique = true),
        @CompoundIndex(name = "draft_interviewer_updated_idx", def = "{'interviewerId': 1, 'updatedAt': -1}")
})
public class FeedbackDraft {
    @Id
    private String id;
    @Indexed
    private String sessionId;
    @Indexed
    private String interviewerId;
    private Integer rating = 0;
    private Integer communication = 0;
    private Integer technicalSkills = 0;
    private String ratingLevel;
    private String strengths;
    private String weaknesses;
    private String hiringRecommendation;
    private String communicationNotes;
    private String codingQualityNotes;
    private String problemSolvingNotes;
    private String finalSummary;
    private String shareableFeedback;
    private String privateNotes;
    private Boolean shareWithInterviewee = true;
    private List<Feedback.TopicFeedback> topicFeedback = new ArrayList<>();
    private Instant createdAt;
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getInterviewerId() { return interviewerId; }
    public void setInterviewerId(String interviewerId) { this.interviewerId = interviewerId; }
    public Integer getRating() { return rating == null ? 0 : rating; }
    public void setRating(Integer rating) { this.rating = rating == null ? 0 : rating; }
    public Integer getCommunication() { return communication == null ? 0 : communication; }
    public void setCommunication(Integer communication) { this.communication = communication == null ? 0 : communication; }
    public Integer getTechnicalSkills() { return technicalSkills == null ? 0 : technicalSkills; }
    public void setTechnicalSkills(Integer technicalSkills) { this.technicalSkills = technicalSkills == null ? 0 : technicalSkills; }
    public String getRatingLevel() { return ratingLevel; }
    public void setRatingLevel(String ratingLevel) { this.ratingLevel = ratingLevel; }
    public String getStrengths() { return strengths; }
    public void setStrengths(String strengths) { this.strengths = strengths; }
    public String getWeaknesses() { return weaknesses; }
    public void setWeaknesses(String weaknesses) { this.weaknesses = weaknesses; }
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
    public String getShareableFeedback() { return shareableFeedback; }
    public void setShareableFeedback(String shareableFeedback) { this.shareableFeedback = shareableFeedback; }
    public String getPrivateNotes() { return privateNotes; }
    public void setPrivateNotes(String privateNotes) { this.privateNotes = privateNotes; }
    public Boolean getShareWithInterviewee() { return shareWithInterviewee == null || shareWithInterviewee; }
    public void setShareWithInterviewee(Boolean shareWithInterviewee) { this.shareWithInterviewee = shareWithInterviewee == null || shareWithInterviewee; }
    public List<Feedback.TopicFeedback> getTopicFeedback() { return topicFeedback == null ? new ArrayList<>() : topicFeedback; }
    public void setTopicFeedback(List<Feedback.TopicFeedback> topicFeedback) { this.topicFeedback = topicFeedback == null ? new ArrayList<>() : topicFeedback; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
