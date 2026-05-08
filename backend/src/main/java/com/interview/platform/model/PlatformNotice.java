package com.interview.platform.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "platform_notices")
public class PlatformNotice {
    @Id
    private String id;
    @Indexed
    private String type;
    private String title;
    private String message;
    @Indexed
    private Boolean active = true;
    private Instant startsAt;
    private Instant endsAt;
    private String createdByAdminId;
    private Instant createdAt;
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getType() { return type == null ? "NOTICE" : type; }
    public void setType(String type) { this.type = normalize(type) == null ? "NOTICE" : normalize(type).toUpperCase(); }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = normalize(title); }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = normalize(message); }
    public Boolean getActive() { return active == null || active; }
    public void setActive(Boolean active) { this.active = active == null || active; }
    public Instant getStartsAt() { return startsAt; }
    public void setStartsAt(Instant startsAt) { this.startsAt = startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public void setEndsAt(Instant endsAt) { this.endsAt = endsAt; }
    public String getCreatedByAdminId() { return createdByAdminId; }
    public void setCreatedByAdminId(String createdByAdminId) { this.createdByAdminId = normalize(createdByAdminId); }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    private String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim().replaceAll("\\s+", " ");
    }
}
