package com.interview.platform.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Document(collection = "sessions")
public class Session {
    @Id
    private String id;
    private String title;
    private String interviewerId;
    private String candidateId;
    private LocalDateTime startTime;

    public Session() {}

    public Session(String title, String interviewerId, String candidateId, LocalDateTime startTime) {
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
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
}
