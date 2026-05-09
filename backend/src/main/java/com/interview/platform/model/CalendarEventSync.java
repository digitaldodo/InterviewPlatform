package com.interview.platform.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "calendar_event_syncs")
@CompoundIndexes({
        @CompoundIndex(name = "calendar_sync_user_session_provider_idx", def = "{'userId': 1, 'sessionId': 1, 'provider': 1}", unique = true),
        @CompoundIndex(name = "calendar_sync_session_idx", def = "{'sessionId': 1}")
})
public class CalendarEventSync {
    @Id
    private String id;
    private String userId;
    private String sessionId;
    private String provider;
    private String externalEventId;
    private String status;
    private Instant lastSyncedAt;
    private String lastError;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getExternalEventId() { return externalEventId; }
    public void setExternalEventId(String externalEventId) { this.externalEventId = externalEventId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getLastSyncedAt() { return lastSyncedAt; }
    public void setLastSyncedAt(Instant lastSyncedAt) { this.lastSyncedAt = lastSyncedAt; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
}
