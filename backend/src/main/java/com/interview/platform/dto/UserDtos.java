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
        private List<String> preferredDomains;
        private String experienceLevel;
        private List<String> availability;

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
        public List<String> getPreferredDomains() { return preferredDomains; }
        public void setPreferredDomains(List<String> preferredDomains) { this.preferredDomains = preferredDomains; }
        public String getExperienceLevel() { return experienceLevel; }
        public void setExperienceLevel(String experienceLevel) { this.experienceLevel = experienceLevel; }
        public List<String> getAvailability() { return availability; }
        public void setAvailability(List<String> availability) { this.availability = availability; }
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
