package com.interview.platform.service;

import com.interview.platform.dto.MeetingDtos;
import com.interview.platform.model.Session;
import com.interview.platform.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.UUID;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class JitsiMeetingProvider implements MeetingProviderGateway {

    private final String domain;
    private final String tenant;

    public JitsiMeetingProvider(
            @Value("${app.meetings.jitsi.domain:meet.jit.si}") String domain,
            @Value("${app.meetings.jitsi.tenant:}") String tenant
    ) {
        this.domain = sanitizeDomain(domain);
        this.tenant = tenant == null ? "" : tenant.trim();
    }

    @Override
    public String getProviderKey() {
        return "JITSI";
    }

    @Override
    public String getLabel() {
        return "In-platform meeting";
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean supportsEmbeddedExperience() {
        return true;
    }

    @Override
    public void provision(Session session, User interviewer, User interviewee) {
        String roomName = session.getMeetingId();
        if (roomName == null || roomName.isBlank()) {
            roomName = buildRoomName(session);
        }
        String joinUrl = meetingUrl(roomName);
        session.setMeetingProvider(getProviderKey());
        session.setMeetingId(roomName);
        session.setJoinUrl(joinUrl);
        session.setHostUrl(joinUrl);
        session.setMeetingStatus(normalizeStatus(session.getMeetingStatus()));
    }

    @Override
    public MeetingDtos.MeetingAccessResponse buildAccess(Session session, User viewer, User interviewer, User interviewee) {
        String displayName = viewer.getName() != null && !viewer.getName().isBlank()
                ? viewer.getName().trim()
                : viewer.getUsername();
        return new MeetingDtos.MeetingAccessResponse(
                session.getId(),
                session.getInterviewType() != null && !session.getInterviewType().isBlank() ? session.getInterviewType() : session.getTitle(),
                getProviderKey(),
                getLabel(),
                session.getMeetingId(),
                normalizeStatus(session.getMeetingStatus()),
                participantRole(viewer, session),
                label(interviewer),
                label(interviewee),
                session.getJoinUrl(),
                session.getHostUrl(),
                viewer.getId().equals(session.getInterviewerId()) && session.getHostUrl() != null && !session.getHostUrl().isBlank()
                        ? withDisplayName(session.getHostUrl(), displayName)
                        : withDisplayName(session.getJoinUrl(), displayName),
                session.getMeetingPasscode(),
                session.getStartTime(),
                session.getDurationMinutes(),
                session.getMeetingStartedAt(),
                session.getMeetingEndedAt(),
                true,
                false,
                "https://" + domain + "/external_api.js",
                domain,
                roomPath(session.getMeetingId()),
                displayName,
                viewer.getEmail(),
                null
        );
    }

    private String buildRoomName(Session session) {
        String seed = (session.getInterviewType() == null || session.getInterviewType().isBlank() ? "interview" : session.getInterviewType())
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (seed.isBlank()) {
            seed = "interview";
        }
        return seed + "-" + UUID.randomUUID().toString().replace("-", "");
    }

    private String meetingUrl(String roomName) {
        return "https://" + domain + "/" + roomPath(roomName);
    }

    private String withDisplayName(String url, String displayName) {
        if (url == null || url.isBlank() || displayName == null || displayName.isBlank()) {
            return url;
        }
        String separator = url.contains("#") ? "&" : "#";
        return url + separator + "userInfo.displayName=%22" + URLEncoder.encode(displayName, StandardCharsets.UTF_8) + "%22";
    }

    private String roomPath(String roomName) {
        String safeRoom = roomName == null ? "" : roomName.trim();
        if (tenant.isBlank()) {
            return safeRoom;
        }
        return tenant + "/" + safeRoom;
    }

    private String label(User user) {
        if (user == null) {
            return "Participant";
        }
        String value = user.getName();
        if (value == null || value.isBlank()) {
            value = user.getUsername();
        }
        return value == null || value.isBlank() ? "Participant" : value;
    }

    private String participantRole(User viewer, Session session) {
        return viewer.getId().equals(session.getInterviewerId()) ? "HOST" : "PARTICIPANT";
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "SCHEDULED";
        }
        return status.trim().toUpperCase(Locale.ROOT);
    }

    private String sanitizeDomain(String value) {
        String cleaned = value == null || value.isBlank() ? "meet.jit.si" : value.trim();
        return cleaned.replaceFirst("^https?://", "").replaceAll("/+$", "");
    }
}
