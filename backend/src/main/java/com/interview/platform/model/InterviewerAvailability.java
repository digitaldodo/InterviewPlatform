package com.interview.platform.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.DayOfWeek;
import java.time.Instant;

@Document(collection = "interviewer_availability")
@CompoundIndex(name = "interviewer_day_window_idx", def = "{'interviewerId': 1, 'dayOfWeek': 1, 'startTime': 1, 'endTime': 1}")
public class InterviewerAvailability {
    @Id
    private String id;
    @Indexed
    private String interviewerId;
    private DayOfWeek dayOfWeek;
    private String startTime;
    private String endTime;
    private Integer durationMinutes;
    private Instant createdAt;
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getInterviewerId() { return interviewerId; }
    public void setInterviewerId(String interviewerId) { this.interviewerId = interviewerId; }
    public DayOfWeek getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(DayOfWeek dayOfWeek) { this.dayOfWeek = dayOfWeek; }
    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }
    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
