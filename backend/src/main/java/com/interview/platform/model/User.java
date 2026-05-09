package com.interview.platform.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "users")
@org.springframework.data.mongodb.core.index.CompoundIndexes({
        @CompoundIndex(name = "multi_role_active_rating_idx", def = "{'roles': 1, 'role': 1, 'isVerified': 1, 'averageRating': -1}"),
        @CompoundIndex(name = "marketplace_visibility_idx", def = "{'roles': 1, 'accountEnabled': 1, 'publicProfileVisible': 1, 'acceptingBookings': 1, 'averageRating': -1}"),
        @CompoundIndex(name = "booking_recommendation_idx", def = "{'roles': 1, 'acceptingBookings': 1, 'completedInterviews': -1}")
})
public class User {

    @Id
    private String id;
    @Indexed
    private String username;
    @Indexed(unique = true, sparse = true)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String usernameKey;
    @Indexed(unique = true)
    private String email;
    private String displayName;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String passwordHash;
    private String role;       // Compatibility primary role for older clients.
    @Indexed
    private List<String> roles = new ArrayList<>();
    private String activeWorkspace;
    @Indexed
    private List<String> skills = new ArrayList<>();
    private List<String> availability = new ArrayList<>();
    private List<String> favoriteInterviewerIds = new ArrayList<>();
    private String company;
    private String currentRole;
    private String bio;
    private String avatarUrl;
    private String language;
    private String timeZone;
    private String preferredMeetingProvider = "JITSI";
    private Boolean emailRemindersEnabled = true;
    private Boolean inAppRemindersEnabled = true;
    private Boolean calendarAutoSyncEnabled = true;
    private List<Integer> reminderOffsetsMinutes = new ArrayList<>(List.of(1440, 60, 30, 10));
    private List<String> preferredDomains = new ArrayList<>();
    private List<String> interviewTopics = new ArrayList<>();
    private List<Integer> sessionDurations = new ArrayList<>();
    private String experienceLevel;
    private Integer yearsExperience = 0;
    private Integer completedInterviews = 0;
    private Integer completedSessions = 0;
    private Integer cancelledSessions = 0;
    private Double averageRating = 0.0;
    private Integer reviewCount = 0;
    private Integer priceCents = 0;
    private Boolean acceptingBookings = true;
    private Boolean isVerified = false;
    private Boolean interviewerVerified = false;
    @Indexed
    private String verificationRequestStatus = "NONE";
    private String verificationRequestNotes;
    private String verificationNotes;
    private String linkedInUrl;
    private String verificationCompanyEmail;
    private Instant verificationRequestedAt;
    private Instant verificationReviewedAt;
    private Instant verificationApprovedAt;
    private Boolean publicProfileVisible = true;
    private Boolean accountEnabled = true;
    private String resumeUrl;
    private String resumeFileName;
    private String resumeContentType;
    private Instant resumeUpdatedAt;
    private Instant createdAt;
    private Instant lastLogin;

    public User() {}

    public User(String username, String email, String password) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.displayName = username;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) {
        this.username = username == null ? null : username.trim().toLowerCase();
        this.usernameKey = normalizeUsernameKey(this.username);
    }

    public String getUsernameKey() { return usernameKey; }
    public void setUsernameKey(String usernameKey) { this.usernameKey = normalizeUsernameKey(usernameKey); }

    public String getDisplayName() {
        if (displayName == null || displayName.isBlank()) {
            return username;
        }
        return displayName;
    }
    public void setDisplayName(String displayName) {
        this.displayName = displayName == null ? null : displayName.trim().replaceAll("\\s+", " ");
    }
    @JsonIgnore
    public boolean hasDisplayName() { return displayName != null && !displayName.isBlank(); }

    public String getName() { return getDisplayName(); }
    public void setName(String name) { setDisplayName(name); }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getRole() {
        if (role == null && roles != null && !roles.isEmpty()) {
            return normalizeRole(roles.get(0));
        }
        role = normalizeRole(role);
        return role;
    }
    public void setRole(String role) {
        this.role = normalizeRole(role);
        if (this.role != null && (roles == null || roles.isEmpty())) {
            roles = new ArrayList<>(List.of(this.role));
        }
        if (activeWorkspace == null && this.role != null) {
            activeWorkspace = this.role;
        }
    }

    public List<String> getRoles() {
        if ((roles == null || roles.isEmpty()) && role != null) {
            String normalizedRole = normalizeRole(role);
            roles = normalizedRole == null ? new ArrayList<>() : new ArrayList<>(List.of(normalizedRole));
        }
        if (roles != null) {
            List<String> normalized = new ArrayList<>();
            for (String item : roles) {
                String value = normalizeRole(item);
                if (value != null && !normalized.contains(value)) {
                    normalized.add(value);
                }
            }
            roles = normalized;
        }
        return roles == null ? new ArrayList<>() : roles;
    }
    public void setRoles(List<String> roles) {
        List<String> normalized = new ArrayList<>();
        if (roles != null) {
            for (String item : roles) {
                String value = normalizeRole(item);
                if (value != null && !normalized.contains(value)) {
                    normalized.add(value);
                }
            }
        }
        this.roles = normalized;
        if (!normalized.isEmpty()) {
            this.role = normalized.contains("INTERVIEWEE") ? "INTERVIEWEE" : normalized.get(0);
            if (activeWorkspace == null || !normalized.contains(activeWorkspace)) {
                activeWorkspace = this.role;
            }
        }
    }

    public String getActiveWorkspace() {
        if (activeWorkspace == null) {
            activeWorkspace = getRole() == null ? "INTERVIEWEE" : getRole();
        }
        return activeWorkspace;
    }
    public void setActiveWorkspace(String activeWorkspace) {
        String normalized = normalizeRole(activeWorkspace);
        this.activeWorkspace = normalized;
    }

    public List<String> getSkills() { return skills; }
    public void setSkills(List<String> skills) { this.skills = skills == null ? new ArrayList<>() : skills; }

    public List<String> getAvailability() { return availability; }
    public void setAvailability(List<String> availability) { this.availability = availability == null ? new ArrayList<>() : availability; }

    public List<String> getFavoriteInterviewerIds() { return favoriteInterviewerIds; }
    public void setFavoriteInterviewerIds(List<String> favoriteInterviewerIds) { this.favoriteInterviewerIds = favoriteInterviewerIds == null ? new ArrayList<>() : favoriteInterviewerIds; }

    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }

    public String getCurrentRole() { return currentRole; }
    public void setCurrentRole(String currentRole) { this.currentRole = currentRole; }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public String getTimeZone() { return timeZone; }
    public void setTimeZone(String timeZone) { this.timeZone = trimToNull(timeZone); }
    public String getPreferredMeetingProvider() {
        return preferredMeetingProvider == null || preferredMeetingProvider.isBlank() ? "JITSI" : preferredMeetingProvider;
    }
    public void setPreferredMeetingProvider(String preferredMeetingProvider) {
        String value = trimToNull(preferredMeetingProvider);
        this.preferredMeetingProvider = value == null ? "JITSI" : value.trim().toUpperCase();
    }
    public Boolean getEmailRemindersEnabled() { return emailRemindersEnabled == null || emailRemindersEnabled; }
    public void setEmailRemindersEnabled(Boolean emailRemindersEnabled) {
        this.emailRemindersEnabled = emailRemindersEnabled == null || emailRemindersEnabled;
    }
    public Boolean getInAppRemindersEnabled() { return inAppRemindersEnabled == null || inAppRemindersEnabled; }
    public void setInAppRemindersEnabled(Boolean inAppRemindersEnabled) {
        this.inAppRemindersEnabled = inAppRemindersEnabled == null || inAppRemindersEnabled;
    }
    public Boolean getCalendarAutoSyncEnabled() { return calendarAutoSyncEnabled == null || calendarAutoSyncEnabled; }
    public void setCalendarAutoSyncEnabled(Boolean calendarAutoSyncEnabled) {
        this.calendarAutoSyncEnabled = calendarAutoSyncEnabled == null || calendarAutoSyncEnabled;
    }
    public List<Integer> getReminderOffsetsMinutes() {
        if (reminderOffsetsMinutes == null || reminderOffsetsMinutes.isEmpty()) {
            reminderOffsetsMinutes = new ArrayList<>(List.of(1440, 60, 30, 10));
        }
        List<Integer> normalized = new ArrayList<>();
        for (Integer value : reminderOffsetsMinutes) {
            if (value == null || !List.of(1440, 60, 30, 10).contains(value)) continue;
            if (!normalized.contains(value)) normalized.add(value);
        }
        if (normalized.isEmpty()) normalized.addAll(List.of(1440, 60, 30, 10));
        reminderOffsetsMinutes = normalized;
        return reminderOffsetsMinutes;
    }
    public void setReminderOffsetsMinutes(List<Integer> reminderOffsetsMinutes) {
        List<Integer> normalized = new ArrayList<>();
        if (reminderOffsetsMinutes != null) {
            for (Integer value : reminderOffsetsMinutes) {
                if (value == null || !List.of(1440, 60, 30, 10).contains(value)) continue;
                if (!normalized.contains(value)) normalized.add(value);
            }
        }
        this.reminderOffsetsMinutes = normalized.isEmpty()
                ? new ArrayList<>(List.of(1440, 60, 30, 10))
                : normalized;
    }

    public List<String> getPreferredDomains() { return preferredDomains; }
    public void setPreferredDomains(List<String> preferredDomains) { this.preferredDomains = preferredDomains == null ? new ArrayList<>() : preferredDomains; }

    public List<String> getInterviewTopics() { return interviewTopics; }
    public void setInterviewTopics(List<String> interviewTopics) { this.interviewTopics = interviewTopics == null ? new ArrayList<>() : interviewTopics; }

    public List<Integer> getSessionDurations() { return sessionDurations == null ? new ArrayList<>() : sessionDurations; }
    public void setSessionDurations(List<Integer> sessionDurations) {
        if (sessionDurations == null) {
            this.sessionDurations = new ArrayList<>();
            return;
        }
        List<Integer> normalized = new ArrayList<>();
        for (Integer value : sessionDurations) {
            if (value == null || value <= 0) continue;
            if (!normalized.contains(value)) normalized.add(value);
        }
        this.sessionDurations = normalized;
    }

    public String getExperienceLevel() { return experienceLevel; }
    public void setExperienceLevel(String experienceLevel) { this.experienceLevel = experienceLevel; }

    public Integer getYearsExperience() { return yearsExperience; }
    public void setYearsExperience(Integer yearsExperience) { this.yearsExperience = yearsExperience == null ? 0 : yearsExperience; }

    public Integer getCompletedInterviews() { return completedInterviews; }
    public void setCompletedInterviews(Integer completedInterviews) { this.completedInterviews = completedInterviews == null ? 0 : completedInterviews; }

    public Integer getCompletedSessions() { return completedSessions; }
    public void setCompletedSessions(Integer completedSessions) { this.completedSessions = completedSessions == null ? 0 : completedSessions; }

    public Integer getCancelledSessions() { return cancelledSessions; }
    public void setCancelledSessions(Integer cancelledSessions) { this.cancelledSessions = cancelledSessions == null ? 0 : cancelledSessions; }

    public Double getAverageRating() { return averageRating; }
    public void setAverageRating(Double averageRating) { this.averageRating = averageRating == null ? 0.0 : averageRating; }

    public Integer getReviewCount() { return reviewCount; }
    public void setReviewCount(Integer reviewCount) { this.reviewCount = reviewCount == null ? 0 : reviewCount; }

    public Integer getPriceCents() { return priceCents; }
    public void setPriceCents(Integer priceCents) { this.priceCents = priceCents == null ? 0 : priceCents; }

    public Boolean getAcceptingBookings() { return acceptingBookings; }
    public void setAcceptingBookings(Boolean acceptingBookings) { this.acceptingBookings = acceptingBookings == null || acceptingBookings; }

    public Boolean getIsVerified() { return isVerified; }
    public void setIsVerified(Boolean verified) { isVerified = verified != null && verified; }

    public Boolean getInterviewerVerified() { return interviewerVerified; }
    public void setInterviewerVerified(Boolean interviewerVerified) { this.interviewerVerified = interviewerVerified != null && interviewerVerified; }

    public String getVerificationRequestStatus() { return verificationRequestStatus == null ? "NONE" : verificationRequestStatus; }
    public void setVerificationRequestStatus(String verificationRequestStatus) {
        this.verificationRequestStatus = trimToNull(verificationRequestStatus) == null ? "NONE" : verificationRequestStatus.trim().toUpperCase();
    }

    public String getVerificationRequestNotes() { return verificationRequestNotes; }
    public void setVerificationRequestNotes(String verificationRequestNotes) { this.verificationRequestNotes = trimToNull(verificationRequestNotes); }

    public String getVerificationNotes() { return verificationNotes; }
    public void setVerificationNotes(String verificationNotes) { this.verificationNotes = trimToNull(verificationNotes); }

    public String getLinkedInUrl() { return linkedInUrl; }
    public void setLinkedInUrl(String linkedInUrl) { this.linkedInUrl = trimToNull(linkedInUrl); }

    public String getVerificationCompanyEmail() { return verificationCompanyEmail; }
    public void setVerificationCompanyEmail(String verificationCompanyEmail) { this.verificationCompanyEmail = trimToNull(verificationCompanyEmail); }

    public Instant getVerificationRequestedAt() { return verificationRequestedAt; }
    public void setVerificationRequestedAt(Instant verificationRequestedAt) { this.verificationRequestedAt = verificationRequestedAt; }

    public Instant getVerificationReviewedAt() { return verificationReviewedAt; }
    public void setVerificationReviewedAt(Instant verificationReviewedAt) { this.verificationReviewedAt = verificationReviewedAt; }

    public Instant getVerificationApprovedAt() { return verificationApprovedAt; }
    public void setVerificationApprovedAt(Instant verificationApprovedAt) { this.verificationApprovedAt = verificationApprovedAt; }

    public Boolean getPublicProfileVisible() { return publicProfileVisible == null || publicProfileVisible; }
    public void setPublicProfileVisible(Boolean publicProfileVisible) {
        this.publicProfileVisible = publicProfileVisible == null || publicProfileVisible;
    }

    @JsonProperty("isPublicProfile")
    public Boolean getIsPublicProfile() {
        return getPublicProfileVisible();
    }

    @JsonProperty("isPublicProfile")
    public void setIsPublicProfile(Boolean isPublicProfile) {
        setPublicProfileVisible(isPublicProfile);
    }

    public Boolean getAccountEnabled() { return accountEnabled; }
    public void setAccountEnabled(Boolean accountEnabled) { this.accountEnabled = accountEnabled == null || accountEnabled; }

    public String getResumeUrl() { return resumeUrl; }
    public void setResumeUrl(String resumeUrl) { this.resumeUrl = trimToNull(resumeUrl); }

    public String getResumeFileName() { return resumeFileName; }
    public void setResumeFileName(String resumeFileName) { this.resumeFileName = trimToNull(resumeFileName); }

    public String getResumeContentType() { return resumeContentType; }
    public void setResumeContentType(String resumeContentType) { this.resumeContentType = trimToNull(resumeContentType); }

    public Instant getResumeUpdatedAt() { return resumeUpdatedAt; }
    public void setResumeUpdatedAt(Instant resumeUpdatedAt) { this.resumeUpdatedAt = resumeUpdatedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getLastLogin() { return lastLogin; }
    public void setLastLogin(Instant lastLogin) { this.lastLogin = lastLogin; }

    public boolean hasRole(String role) {
        String normalized = normalizeRole(role);
        return normalized != null && getRoles().contains(normalized);
    }

    private String normalizeRole(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toUpperCase();
        return switch (normalized) {
            case "INTERVIEWER", "INTERVIEWEE", "ADMIN" -> normalized;
            default -> normalized;
        };
    }

    private String normalizeUsernameKey(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim().toLowerCase();
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
