package com.interview.platform.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "calendar_connections")
@CompoundIndex(name = "calendar_provider_user_idx", def = "{'provider': 1, 'userId': 1}", unique = true)
public class CalendarConnection {
    @Id
    private String id;
    private String userId;
    private String provider;
    private String accountEmail;
    private String encryptedAccessToken;
    private String encryptedRefreshToken;
    private Instant accessTokenExpiresAt;
    private String scopes;
    private String status = "CONNECTED";
    private Instant connectedAt;
    private Instant updatedAt;
    private Instant lastSyncAt;
    private String lastSyncStatus;
    private String lastSyncMessage;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getAccountEmail() { return accountEmail; }
    public void setAccountEmail(String accountEmail) { this.accountEmail = trimToNull(accountEmail); }
    public String getEncryptedAccessToken() { return encryptedAccessToken; }
    public void setEncryptedAccessToken(String encryptedAccessToken) { this.encryptedAccessToken = encryptedAccessToken; }
    public String getEncryptedRefreshToken() { return encryptedRefreshToken; }
    public void setEncryptedRefreshToken(String encryptedRefreshToken) { this.encryptedRefreshToken = encryptedRefreshToken; }
    public Instant getAccessTokenExpiresAt() { return accessTokenExpiresAt; }
    public void setAccessTokenExpiresAt(Instant accessTokenExpiresAt) { this.accessTokenExpiresAt = accessTokenExpiresAt; }
    public String getScopes() { return scopes; }
    public void setScopes(String scopes) { this.scopes = trimToNull(scopes); }
    public String getStatus() { return status == null || status.isBlank() ? "DISCONNECTED" : status; }
    public void setStatus(String status) { this.status = status == null || status.isBlank() ? "DISCONNECTED" : status.trim().toUpperCase(); }
    public Instant getConnectedAt() { return connectedAt; }
    public void setConnectedAt(Instant connectedAt) { this.connectedAt = connectedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Instant getLastSyncAt() { return lastSyncAt; }
    public void setLastSyncAt(Instant lastSyncAt) { this.lastSyncAt = lastSyncAt; }
    public String getLastSyncStatus() { return lastSyncStatus; }
    public void setLastSyncStatus(String lastSyncStatus) { this.lastSyncStatus = trimToNull(lastSyncStatus); }
    public String getLastSyncMessage() { return lastSyncMessage; }
    public void setLastSyncMessage(String lastSyncMessage) { this.lastSyncMessage = trimToNull(lastSyncMessage); }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
