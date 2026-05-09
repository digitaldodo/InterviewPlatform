package com.interview.platform.dto;

import java.time.Instant;
import java.util.List;

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

    public record CalendarProviderOption(
            String provider,
            String label,
            boolean configured,
            boolean connected,
            String status,
            String accountEmail,
            Instant connectedAt,
            Instant lastSyncAt,
            String lastSyncStatus,
            String lastSyncMessage
    ) {}

    public record CalendarSettingsResponse(
            List<CalendarProviderOption> providers,
            String defaultTimezone,
            String preferredMeetingProvider,
            boolean emailRemindersEnabled,
            boolean inAppRemindersEnabled,
            boolean calendarAutoSyncEnabled,
            List<Integer> reminderOffsetsMinutes
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
