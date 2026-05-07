package com.interview.platform.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class BookingRequest {
    @NotBlank
    private String interviewerId;
    @NotBlank
    private String intervieweeId;
    @NotBlank
    private String interviewType;
    @NotBlank
    private String startTime;
    @Min(15)
    @Max(480)
    private Integer durationMinutes;
    @Size(max = 2000)
    private String notes;
    private String meetingProvider;

    public String getInterviewerId() { return interviewerId; }
    public void setInterviewerId(String interviewerId) { this.interviewerId = interviewerId; }
    public String getIntervieweeId() { return intervieweeId; }
    public void setIntervieweeId(String intervieweeId) { this.intervieweeId = intervieweeId; }
    public String getInterviewType() { return interviewType; }
    public void setInterviewType(String interviewType) { this.interviewType = interviewType; }
    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getMeetingProvider() { return meetingProvider; }
    public void setMeetingProvider(String meetingProvider) { this.meetingProvider = meetingProvider; }
}
