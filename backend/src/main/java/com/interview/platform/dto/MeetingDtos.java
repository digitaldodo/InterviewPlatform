package com.interview.platform.dto;

import java.time.Instant;

public final class MeetingDtos {

    private MeetingDtos() {}

    public record MeetingSessionState(
            String sessionStatus,
            String liveState,
            String guidance,
            Instant joinAvailableAt,
            Instant accessExpiresAt,
            boolean canStart,
            boolean canJoin,
            boolean waitingForHost,
            boolean sensitiveAccessExposed
    ) {}

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
            String jwt,
            MeetingSessionState sessionState
    ) {
        public MeetingAccessResponse(
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
        ) {
            this(
                    sessionId,
                    sessionTitle,
                    meetingProvider,
                    providerLabel,
                    meetingId,
                    meetingStatus,
                    participantRole,
                    interviewerName,
                    intervieweeName,
                    joinUrl,
                    hostUrl,
                    launchUrl,
                    meetingPasscode,
                    scheduledAt,
                    durationMinutes,
                    meetingStartedAt,
                    meetingEndedAt,
                    canEmbed,
                    externalLaunchRequired,
                    embedScriptUrl,
                    embedDomain,
                    roomName,
                    displayName,
                    email,
                    jwt,
                    null
            );
        }

        public MeetingAccessResponse withState(MeetingSessionState state) {
            return new MeetingAccessResponse(
                    sessionId,
                    sessionTitle,
                    meetingProvider,
                    providerLabel,
                    meetingId,
                    meetingStatus,
                    participantRole,
                    interviewerName,
                    intervieweeName,
                    joinUrl,
                    hostUrl,
                    launchUrl,
                    meetingPasscode,
                    scheduledAt,
                    durationMinutes,
                    meetingStartedAt,
                    meetingEndedAt,
                    canEmbed,
                    externalLaunchRequired,
                    embedScriptUrl,
                    embedDomain,
                    roomName,
                    displayName,
                    email,
                    jwt,
                    state
            );
        }

        public MeetingAccessResponse withoutSensitiveAccess(MeetingSessionState state) {
            return new MeetingAccessResponse(
                    sessionId,
                    sessionTitle,
                    meetingProvider,
                    providerLabel,
                    null,
                    meetingStatus,
                    participantRole,
                    interviewerName,
                    intervieweeName,
                    null,
                    null,
                    null,
                    null,
                    scheduledAt,
                    durationMinutes,
                    meetingStartedAt,
                    meetingEndedAt,
                    false,
                    externalLaunchRequired,
                    null,
                    null,
                    null,
                    displayName,
                    email,
                    null,
                    state
            );
        }
    }
}
