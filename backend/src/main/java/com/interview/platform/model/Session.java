package com.interview.platform.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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
    private List<String> topics = new ArrayList<>();
    private Integer durationMinutes = 45;
    private String meetingLink;
    private String meetingId;
    private String meetingProvider;
    private String joinUrl;
    private String hostUrl;
    private String meetingPasscode;
    private String meetingStatus;
    private Instant meetingStartedAt;
    private Instant meetingEndedAt;
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
    public void setInterviewType(String interviewType) {
        this.interviewType = trimToNull(interviewType);
        if ((topics == null || topics.isEmpty()) && this.interviewType != null) {
            topics = normalizeTopics(List.of(this.interviewType));
        }
    }
    public List<String> getTopics() {
        if ((topics == null || topics.isEmpty()) && interviewType != null && !interviewType.isBlank()) {
            topics = normalizeTopics(List.of(interviewType));
        }
        if ((topics == null || topics.isEmpty()) && title != null && !title.isBlank()) {
            topics = normalizeTopics(List.of(title));
        }
        return topics == null ? new ArrayList<>() : topics;
    }
    public void setTopics(List<String> topics) {
        this.topics = normalizeTopics(topics);
        if (!this.topics.isEmpty()) {
            String summary = String.join(", ", this.topics);
            this.interviewType = summary;
            if (title == null || title.isBlank()) {
                title = summary;
            }
        }
    }
    public Integer getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes == null ? 45 : durationMinutes; }
    public String getMeetingLink() {
        if (meetingLink == null || meetingLink.isBlank()) {
            return joinUrl;
        }
        return meetingLink;
    }
    public void setMeetingLink(String meetingLink) {
        this.meetingLink = meetingLink;
        if ((this.joinUrl == null || this.joinUrl.isBlank()) && meetingLink != null && !meetingLink.isBlank()) {
            this.joinUrl = meetingLink;
        }
    }
    public String getMeetingId() { return meetingId; }
    public void setMeetingId(String meetingId) { this.meetingId = meetingId; }
    public String getMeetingProvider() { return meetingProvider; }
    public void setMeetingProvider(String meetingProvider) { this.meetingProvider = meetingProvider; }
    public String getJoinUrl() {
        if ((joinUrl == null || joinUrl.isBlank()) && meetingLink != null && !meetingLink.isBlank()) {
            return meetingLink;
        }
        return joinUrl;
    }
    public void setJoinUrl(String joinUrl) {
        this.joinUrl = joinUrl;
        if ((this.meetingLink == null || this.meetingLink.isBlank()) && joinUrl != null && !joinUrl.isBlank()) {
            this.meetingLink = joinUrl;
        }
    }
    public String getHostUrl() { return hostUrl; }
    public void setHostUrl(String hostUrl) { this.hostUrl = hostUrl; }
    public String getMeetingPasscode() { return meetingPasscode; }
    public void setMeetingPasscode(String meetingPasscode) { this.meetingPasscode = meetingPasscode; }
    public String getMeetingStatus() { return meetingStatus; }
    public void setMeetingStatus(String meetingStatus) { this.meetingStatus = meetingStatus; }
    public Instant getMeetingStartedAt() { return meetingStartedAt; }
    public void setMeetingStartedAt(Instant meetingStartedAt) { this.meetingStartedAt = meetingStartedAt; }
    public Instant getMeetingEndedAt() { return meetingEndedAt; }
    public void setMeetingEndedAt(Instant meetingEndedAt) { this.meetingEndedAt = meetingEndedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    private List<String> normalizeTopics(List<String> values) {
        List<String> normalized = new ArrayList<>();
        if (values == null) return normalized;
        for (String value : values) {
            if (value == null) continue;
            for (String part : value.split(",")) {
                String topic = trimToNull(part);
                if (topic != null && normalized.stream().noneMatch(item -> item.equalsIgnoreCase(topic))) {
                    normalized.add(topic);
                }
            }
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
