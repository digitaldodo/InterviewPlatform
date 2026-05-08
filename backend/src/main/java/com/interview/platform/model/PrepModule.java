package com.interview.platform.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "prep_modules")
public class PrepModule {
    @Id
    private String id;
    private String title;
    private String description;
    @Indexed
    private String category;
    private String difficulty;
    private List<String> tags = new ArrayList<>();
    private Integer estimatedDurationMinutes;
    @Indexed
    private String visibilityStatus = "DRAFT";
    private List<ResourceLink> resources = new ArrayList<>();
    private String createdByAdminId;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant publishedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = trimToNull(title); }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = trimToNull(description); }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = trimToNull(category); }

    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = trimToNull(difficulty); }

    public List<String> getTags() { return tags == null ? new ArrayList<>() : tags; }
    public void setTags(List<String> tags) { this.tags = normalizeList(tags); }

    public Integer getEstimatedDurationMinutes() { return estimatedDurationMinutes; }
    public void setEstimatedDurationMinutes(Integer estimatedDurationMinutes) {
        this.estimatedDurationMinutes = estimatedDurationMinutes == null || estimatedDurationMinutes <= 0 ? null : estimatedDurationMinutes;
    }

    public String getVisibilityStatus() { return visibilityStatus == null ? "DRAFT" : visibilityStatus; }
    public void setVisibilityStatus(String visibilityStatus) {
        String normalized = trimToNull(visibilityStatus);
        this.visibilityStatus = normalized == null ? "DRAFT" : normalized.toUpperCase();
    }

    public List<ResourceLink> getResources() { return resources == null ? new ArrayList<>() : resources; }
    public void setResources(List<ResourceLink> resources) { this.resources = resources == null ? new ArrayList<>() : resources; }

    public String getCreatedByAdminId() { return createdByAdminId; }
    public void setCreatedByAdminId(String createdByAdminId) { this.createdByAdminId = trimToNull(createdByAdminId); }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }

    public static class ResourceLink {
        private String label;
        private String url;

        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = trimToNull(label); }

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = trimToNull(url); }
    }

    private static List<String> normalizeList(List<String> values) {
        List<String> normalized = new ArrayList<>();
        if (values == null) return normalized;
        for (String value : values) {
            String item = trimToNull(value);
            if (item != null && !normalized.contains(item)) normalized.add(item);
        }
        return normalized;
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim().replaceAll("\\s+", " ");
    }
}
