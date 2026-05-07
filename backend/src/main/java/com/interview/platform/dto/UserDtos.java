package com.interview.platform.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

public class UserDtos {
    public static class ProfileUpdateRequest {
        @Size(min = 1, max = 120)
        private String name;
        @Size(min = 1, max = 120)
        private String displayName;
        @Size(min = 3, max = 24)
        private String username;
        private String avatarUrl;
        @Size(max = 1000)
        private String bio;
        private List<String> skills;
        private String language;
        private String timeZone;
        private List<String> preferredDomains;
        private List<String> interviewTopics;
        private List<Integer> sessionDurations;
        private String experienceLevel;
        private String company;
        private String currentRole;
        private Integer yearsExperience;
        private List<String> availability;
        private Boolean acceptingBookings;
        private Boolean publicProfileVisible;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getAvatarUrl() { return avatarUrl; }
        public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
        public String getBio() { return bio; }
        public void setBio(String bio) { this.bio = bio; }
        public List<String> getSkills() { return skills; }
        public void setSkills(List<String> skills) { this.skills = skills; }
        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
        public String getTimeZone() { return timeZone; }
        public void setTimeZone(String timeZone) { this.timeZone = timeZone; }
        public List<String> getPreferredDomains() { return preferredDomains; }
        public void setPreferredDomains(List<String> preferredDomains) { this.preferredDomains = preferredDomains; }
        public List<String> getInterviewTopics() { return interviewTopics; }
        public void setInterviewTopics(List<String> interviewTopics) { this.interviewTopics = interviewTopics; }
        public List<Integer> getSessionDurations() { return sessionDurations; }
        public void setSessionDurations(List<Integer> sessionDurations) { this.sessionDurations = sessionDurations; }
        public String getExperienceLevel() { return experienceLevel; }
        public void setExperienceLevel(String experienceLevel) { this.experienceLevel = experienceLevel; }
        public String getCompany() { return company; }
        public void setCompany(String company) { this.company = company; }
        public String getCurrentRole() { return currentRole; }
        public void setCurrentRole(String currentRole) { this.currentRole = currentRole; }
        public Integer getYearsExperience() { return yearsExperience; }
        public void setYearsExperience(Integer yearsExperience) { this.yearsExperience = yearsExperience; }
        public List<String> getAvailability() { return availability; }
        public void setAvailability(List<String> availability) { this.availability = availability; }
        public Boolean getAcceptingBookings() { return acceptingBookings; }
        public void setAcceptingBookings(Boolean acceptingBookings) { this.acceptingBookings = acceptingBookings; }
        public Boolean getPublicProfileVisible() { return publicProfileVisible; }
        public void setPublicProfileVisible(Boolean publicProfileVisible) { this.publicProfileVisible = publicProfileVisible; }
    }

    public static class AddRoleRequest {
        private String role;

        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
    }

    public static class DeleteAccountRequest {
        private String password;
        private String confirmation;

        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getConfirmation() { return confirmation; }
        public void setConfirmation(String confirmation) { this.confirmation = confirmation; }
    }

    public static class ChangePasswordRequest {
        private String currentPassword;
        @Size(min = 6)
        private String newPassword;

        public String getCurrentPassword() { return currentPassword; }
        public void setCurrentPassword(String currentPassword) { this.currentPassword = currentPassword; }
        public String getNewPassword() { return newPassword; }
        public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
    }
}
