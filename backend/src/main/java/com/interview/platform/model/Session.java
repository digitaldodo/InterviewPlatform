package com.interview.platform.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "sessions")
public class Session {
    @Id
    private String id;
    private String title;
    private String interviewerId;
    private String candidateId;
    private String startTime;
    private String status;
    private String notes;

    public Session() {}

    public Session(String title, String interviewerId, String candidateId, String startTime) {
        this.title = title;
        this.interviewerId = interviewerId;
        this.candidateId = candidateId;
        this.startTime = startTime;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getInterviewerId() { return interviewerId; }
    public void setInterviewerId(String interviewerId) { this.interviewerId = interviewerId; }
    public String getCandidateId() { return candidateId; }
    public void setCandidateId(String candidateId) { this.candidateId = candidateId; }
    public String getIntervieweeId() { return candidateId; }
    public void setIntervieweeId(String intervieweeId) { this.candidateId = intervieweeId; }
    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }
    public String getTopic() { return title; }
    public void setTopic(String topic) { this.title = topic; }
    public String getScheduledAt() { return startTime; }
    public void setScheduledAt(String scheduledAt) { this.startTime = scheduledAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
