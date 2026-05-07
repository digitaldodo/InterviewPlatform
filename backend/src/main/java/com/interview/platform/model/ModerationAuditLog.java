package com.interview.platform.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Document(collection = "moderation_audit_logs")
@CompoundIndex(name = "entity_created_idx", def = "{'entityType': 1, 'entityId': 1, 'createdAt': -1}")
public class ModerationAuditLog {
    @Id
    private String id;
    @Indexed
    private String entityType;
    @Indexed
    private String entityId;
    @Indexed
    private String actorUserId;
    @Indexed
    private String subjectUserId;
    private String action;
    private String reason;
    private String summary;
    private Map<String, Object> beforeState = new HashMap<>();
    private Map<String, Object> afterState = new HashMap<>();
    private Instant createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }
    public String getActorUserId() { return actorUserId; }
    public void setActorUserId(String actorUserId) { this.actorUserId = actorUserId; }
    public String getSubjectUserId() { return subjectUserId; }
    public void setSubjectUserId(String subjectUserId) { this.subjectUserId = subjectUserId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public Map<String, Object> getBeforeState() { return beforeState == null ? new HashMap<>() : beforeState; }
    public void setBeforeState(Map<String, Object> beforeState) { this.beforeState = beforeState == null ? new HashMap<>() : beforeState; }
    public Map<String, Object> getAfterState() { return afterState == null ? new HashMap<>() : afterState; }
    public void setAfterState(Map<String, Object> afterState) { this.afterState = afterState == null ? new HashMap<>() : afterState; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
