package com.interview.platform.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "sessions")
@CompoundIndex(name = "interviewer_time_status_idx", def = "{'interviewerId': 1, 'startTime': 1, 'status': 1}")
public class Session {
    @Id
    private String id;
    private String title;
    @Indexed
    private String interviewerId;
    @Indexed
    private String candidateId;
    @Indexed
    private String startTime;
    @Indexed
    private String status;
    private String notes;
    private String interviewType;
    private Integer durationMinutes = 45;
    private String meetingLink;
    private String meetingStatus;
    private Instant createdAt;
    private Instant updatedAt;

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
    public String getInterviewType() { return interviewType; }
    public void setInterviewType(String interviewType) { this.interviewType = interviewType; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes == null ? 45 : durationMinutes; }
    public String getMeetingLink() { return meetingLink; }
    public void setMeetingLink(String meetingLink) { this.meetingLink = meetingLink; }
    public String getMeetingStatus() { return meetingStatus; }
    public void setMeetingStatus(String meetingStatus) { this.meetingStatus = meetingStatus; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
