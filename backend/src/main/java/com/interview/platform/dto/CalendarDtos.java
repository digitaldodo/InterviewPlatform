package com.interview.platform.dto;

import java.time.Instant;

public final class CalendarDtos {
    private CalendarDtos() {}

    public record CalendarConnectionStatus(
            String provider,
            boolean configured,
            boolean connected,
            String status,
            String accountEmail,
            Instant connectedAt,
            Instant lastSyncAt,
            String lastSyncStatus,
            String lastSyncMessage
    ) {}

    public record CalendarConnectResponse(String authorizationUrl) {}

    public record CalendarSyncResponse(
            boolean attempted,
            int synced,
            int failed,
            String message,
            Instant syncedAt
    ) {}
}
