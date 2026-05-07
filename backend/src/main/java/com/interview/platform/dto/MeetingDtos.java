package com.interview.platform.dto;

import java.time.Instant;

public final class MeetingDtos {

    private MeetingDtos() {}

    public record MeetingProviderOption(
            String key,
            String label,
            boolean embedded,
            boolean enabled,
            boolean isDefault
    ) {}

    public record MeetingAccessResponse(
            String sessionId,
            String sessionTitle,
            String meetingProvider,
            String providerLabel,
            String meetingId,
            String meetingStatus,
            String participantRole,
            String interviewerName,
            String intervieweeName,
            String joinUrl,
            String hostUrl,
            String launchUrl,
            String meetingPasscode,
            String scheduledAt,
            Integer durationMinutes,
            Instant meetingStartedAt,
            Instant meetingEndedAt,
            boolean canEmbed,
            boolean externalLaunchRequired,
            String embedScriptUrl,
            String embedDomain,
            String roomName,
            String displayName,
            String email,
            String jwt
    ) {}
}
