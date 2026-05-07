package com.interview.platform.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;

public class AvailabilityDtos {

    public static class UpsertRequest {
        @NotBlank
        private String dayOfWeek;
        @NotBlank
        @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "must be in HH:mm format")
        private String startTime;
        @NotBlank
        @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "must be in HH:mm format")
        private String endTime;
        @NotNull
        @Min(15)
        @Max(480)
        private Integer durationMinutes;

        public String getDayOfWeek() { return dayOfWeek; }
        public void setDayOfWeek(String dayOfWeek) { this.dayOfWeek = dayOfWeek; }
        public String getStartTime() { return startTime; }
        public void setStartTime(String startTime) { this.startTime = startTime; }
        public String getEndTime() { return endTime; }
        public void setEndTime(String endTime) { this.endTime = endTime; }
        public Integer getDurationMinutes() { return durationMinutes; }
        public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }
    }

    public static class AvailabilityResponse {
        private String id;
        private String interviewerId;
        private String dayOfWeek;
        private String startTime;
        private String endTime;
        private Integer durationMinutes;
        private Instant createdAt;
        private Instant updatedAt;

        public AvailabilityResponse() {}

        public AvailabilityResponse(String id, String interviewerId, String dayOfWeek, String startTime,
                                    String endTime, Integer durationMinutes, Instant createdAt, Instant updatedAt) {
            this.id = id;
            this.interviewerId = interviewerId;
            this.dayOfWeek = dayOfWeek;
            this.startTime = startTime;
            this.endTime = endTime;
            this.durationMinutes = durationMinutes;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getInterviewerId() { return interviewerId; }
        public void setInterviewerId(String interviewerId) { this.interviewerId = interviewerId; }
        public String getDayOfWeek() { return dayOfWeek; }
        public void setDayOfWeek(String dayOfWeek) { this.dayOfWeek = dayOfWeek; }
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

    public static class GeneratedSlotResponse {
        private String availabilityId;
        private String startTime;
        private String endTime;
        private Integer durationMinutes;

        public GeneratedSlotResponse() {}

        public GeneratedSlotResponse(String availabilityId, String startTime, String endTime, Integer durationMinutes) {
            this.availabilityId = availabilityId;
            this.startTime = startTime;
            this.endTime = endTime;
            this.durationMinutes = durationMinutes;
        }

        public String getAvailabilityId() { return availabilityId; }
        public void setAvailabilityId(String availabilityId) { this.availabilityId = availabilityId; }
        public String getStartTime() { return startTime; }
        public void setStartTime(String startTime) { this.startTime = startTime; }
        public String getEndTime() { return endTime; }
        public void setEndTime(String endTime) { this.endTime = endTime; }
        public Integer getDurationMinutes() { return durationMinutes; }
        public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }
    }
}
