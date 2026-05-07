package com.interview.platform.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "users")
@CompoundIndex(name = "role_active_rating_idx", def = "{'role': 1, 'isVerified': 1, 'averageRating': -1}")
public class User {

    @Id
    private String id;
    @Indexed
    private String username;
    @Indexed(unique = true)
    private String email;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String passwordHash;
    private String role;       // e.g. "INTERVIEWER" or "INTERVIEWEE"
    @Indexed
    private List<String> skills = new ArrayList<>();
    private List<String> availability = new ArrayList<>();
    private List<String> favoriteInterviewerIds = new ArrayList<>();
    private String company;
    private String currentRole;
    private String bio;
    private String avatarUrl;
    private String language;
    private Integer yearsExperience = 0;
    private Integer completedInterviews = 0;
    private Double averageRating = 0.0;
    private Integer reviewCount = 0;
    private Integer priceCents = 0;
    private Boolean acceptingBookings = true;
    private Boolean isVerified = false;
    private Instant createdAt;
    private Instant lastLogin;

    public User() {}

    public User(String username, String email, String password) {
        this.username = username;
        this.email = email;
        this.password = password;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getName() { return username; }
    public void setName(String name) { this.username = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role == null ? null : role.toUpperCase(); }

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

    public Integer getYearsExperience() { return yearsExperience; }
    public void setYearsExperience(Integer yearsExperience) { this.yearsExperience = yearsExperience == null ? 0 : yearsExperience; }

    public Integer getCompletedInterviews() { return completedInterviews; }
    public void setCompletedInterviews(Integer completedInterviews) { this.completedInterviews = completedInterviews == null ? 0 : completedInterviews; }

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

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getLastLogin() { return lastLogin; }
    public void setLastLogin(Instant lastLogin) { this.lastLogin = lastLogin; }
}
