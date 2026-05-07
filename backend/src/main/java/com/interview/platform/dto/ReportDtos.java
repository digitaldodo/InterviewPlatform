package com.interview.platform.dto;

public class ReportDtos {
    public static class CreateReportRequest {
        private String reportedUserId;
        private String sessionId;
        private String reason;
        private String details;

        public String getReportedUserId() { return reportedUserId; }
        public void setReportedUserId(String reportedUserId) { this.reportedUserId = reportedUserId; }
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public String getDetails() { return details; }
        public void setDetails(String details) { this.details = details; }
    }
}
