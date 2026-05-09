package com.interview.platform.service;

import com.interview.platform.model.Session;
import com.interview.platform.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class CalendarInviteService {
    private static final DateTimeFormatter ICS_UTC_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'", Locale.ENGLISH).withZone(ZoneId.of("UTC"));
    private static final DateTimeFormatter DISPLAY_FORMATTER =
            DateTimeFormatter.ofPattern("EEE, MMM d, yyyy 'at' h:mm a z", Locale.ENGLISH);
    private static final String PROD_ID = "-//InterviewPrep//Interview Session Calendar//EN";

    private final SchedulingTimeService schedulingTimeService;
    private final String frontendUrl;

    public CalendarInviteService(SchedulingTimeService schedulingTimeService,
                                 @Value("${app.frontend-url:http://localhost:5500}") String frontendUrl) {
        this.schedulingTimeService = schedulingTimeService;
        this.frontendUrl = frontendUrl;
    }

    public CalendarInvite sessionInvite(Session session, User interviewer, User interviewee, User recipient,
                                        String meetingLink, CalendarInviteType type) {
        OffsetDateTime scheduled = schedulingTimeService.parseStartTime(session.getStartTime());
        Instant start = scheduled.toInstant();
        Instant end = start.plus(Duration.ofMinutes(effectiveDurationMinutes(session)));
        ZoneId recipientZone = recipientZone(recipient).orElse(ZoneId.of("UTC"));
        String method = type == CalendarInviteType.CANCEL ? "CANCEL" : "REQUEST";
        String status = type == CalendarInviteType.CANCEL
                ? "CANCELLED"
                : "CONFIRMED".equalsIgnoreCase(session.getStatus()) ? "CONFIRMED" : "TENTATIVE";
        String title = title(session);
        String filename = safeFileName("interviewprep-" + stableSessionId(session) + ".ics");

        List<String> lines = new ArrayList<>();
        lines.add("BEGIN:VCALENDAR");
        lines.add("PRODID:" + PROD_ID);
        lines.add("VERSION:2.0");
        lines.add("CALSCALE:GREGORIAN");
        lines.add("METHOD:" + method);
        lines.add("X-WR-CALNAME:" + escapeText("InterviewPrep Sessions"));
        lines.add("X-WR-TIMEZONE:" + recipientZone.getId());
        lines.add("X-INTERVIEWPREP-BRAND:" + escapeText("InterviewPrep"));
        lines.add("X-INTERVIEWPREP-SESSION-ID:" + escapeText(stableSessionId(session)));
        lines.add("BEGIN:VEVENT");
        lines.add("UID:" + uid(session));
        lines.add("DTSTAMP:" + formatUtc(schedulingTimeService.nowInstant()));
        lines.add("DTSTART:" + formatUtc(start));
        lines.add("DTEND:" + formatUtc(end));
        lines.add("SEQUENCE:" + sequence(session, type));
        lines.add("STATUS:" + status);
        lines.add("TRANSP:OPAQUE");
        lines.add("CATEGORIES:" + escapeText("InterviewPrep,Mock Interview"));
        lines.add("X-INTERVIEWPREP-MEETING-PROVIDER:" + escapeText(session == null || session.getMeetingProvider() == null ? "JITSI" : session.getMeetingProvider()));
        lines.add("X-INTERVIEWPREP-UPDATED-AT:" + escapeText(session == null || session.getUpdatedAt() == null ? "" : session.getUpdatedAt().toString()));
        if (session != null && session.getCancelledAt() != null) {
            lines.add("X-INTERVIEWPREP-CANCELLED-AT:" + escapeText(session.getCancelledAt().toString()));
        }
        if (session != null && session.getRescheduledAt() != null) {
            lines.add("X-INTERVIEWPREP-RESCHEDULED-AT:" + escapeText(session.getRescheduledAt().toString()));
        }
        lines.add("SUMMARY:" + escapeText(title));
        if (meetingLink != null && !meetingLink.isBlank()) {
            lines.add("LOCATION:" + escapeText(meetingLink));
            lines.add("URL:" + escapeText(meetingLink));
        }
        addOrganizer(lines);
        addAttendee(lines, interviewer, "CHAIR");
        addAttendee(lines, interviewee, "REQ-PARTICIPANT");
        lines.add("DESCRIPTION:" + escapeText(description(session, interviewer, interviewee, recipientZone, meetingLink, type)));
        if (type == CalendarInviteType.CANCEL) {
            lines.add("COMMENT:" + escapeText("InterviewPrep cancellation notice. Calendar clients should remove or mark this event as cancelled."));
        } else if (type == CalendarInviteType.UPDATE) {
            lines.add("COMMENT:" + escapeText("InterviewPrep calendar update. Please keep this latest event version."));
        }
        lines.add("END:VEVENT");
        lines.add("END:VCALENDAR");
        return new CalendarInvite(filename, method, fold(String.join("\r\n", lines)));
    }

    public String downloadFileName(Session session) {
        return safeFileName("interviewprep-" + stableSessionId(session) + ".ics");
    }

    private String description(Session session, User interviewer, User interviewee, ZoneId recipientZone,
                               String meetingLink, CalendarInviteType type) {
        Instant start = schedulingTimeService.parseStartTime(session.getStartTime()).toInstant();
        ZonedDateTime localStart = start.atZone(recipientZone);
        ZonedDateTime localEnd = start.plus(Duration.ofMinutes(effectiveDurationMinutes(session))).atZone(recipientZone);
        String lifecycle = type == CalendarInviteType.CANCEL
                ? "This InterviewPrep session has been cancelled."
                : type == CalendarInviteType.UPDATE
                ? "This is the latest InterviewPrep calendar update for your session."
                : "Your InterviewPrep mock interview session is scheduled.";
        String topics = topics(session);
        String dashboardLink = dashboardUrl(session);
        return String.join("\n",
                "InterviewPrep mock interview",
                "",
                lifecycle,
                "Session: " + title(session),
                "Interviewer: " + displayName(interviewer) + " (role: interviewer)",
                "Interviewee: " + displayName(interviewee) + " (role: interviewee)",
                "Topics: " + topics,
                "Local time: " + DISPLAY_FORMATTER.format(localStart) + " - " + DISPLAY_FORMATTER.format(localEnd),
                "Timezone: " + recipientZone.getId(),
                meetingLink == null || meetingLink.isBlank() ? "Meeting link: Available from your InterviewPrep dashboard." : "Meeting link: " + meetingLink,
                "Meeting provider: " + (session == null || session.getMeetingProvider() == null ? "JITSI" : session.getMeetingProvider()),
                session == null || session.getCancelledAt() == null ? "" : "Cancelled at: " + session.getCancelledAt(),
                session == null || session.getRescheduledAt() == null ? "" : "Rescheduled at: " + session.getRescheduledAt(),
                "Dashboard: " + dashboardLink,
                "",
                "If anything looks wrong, contact InterviewPrep support from your dashboard before the session starts."
        );
    }

    private String title(Session session) {
        String base = session == null ? null : session.getTitle();
        if (base == null || base.isBlank()) {
            base = topics(session);
        }
        return "InterviewPrep: " + (base == null || base.isBlank() ? "Mock interview" : base);
    }

    private String topics(Session session) {
        if (session == null || session.getTopics() == null || session.getTopics().isEmpty()) {
            return "Mock interview";
        }
        return String.join(", ", session.getTopics());
    }

    private int effectiveDurationMinutes(Session session) {
        Integer duration = session == null ? null : session.getDurationMinutes();
        return duration == null || duration <= 0 ? 45 : duration;
    }

    private Optional<ZoneId> recipientZone(User recipient) {
        if (recipient == null || recipient.getTimeZone() == null || recipient.getTimeZone().isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(ZoneId.of(recipient.getTimeZone().trim()));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private String formatUtc(Instant instant) {
        return ICS_UTC_FORMATTER.format(instant);
    }

    private String uid(Session session) {
        return "interviewprep-session-" + stableSessionId(session) + "@interviewprep";
    }

    private String stableSessionId(Session session) {
        if (session != null && session.getId() != null && !session.getId().isBlank()) {
            return session.getId();
        }
        String seed = session == null ? "unknown" : String.join("-", safe(session.getInterviewerId()), safe(session.getCandidateId()), safe(session.getStartTime()));
        return Integer.toHexString(seed.hashCode());
    }

    private int sequence(Session session, CalendarInviteType type) {
        if (type == CalendarInviteType.CANCEL) {
            return 2;
        }
        if (type == CalendarInviteType.UPDATE) {
            return 1;
        }
        if (session != null && session.getUpdatedAt() != null && session.getCreatedAt() != null && session.getUpdatedAt().isAfter(session.getCreatedAt())) {
            return 1;
        }
        return 0;
    }

    private void addOrganizer(List<String> lines) {
        lines.add("ORGANIZER;CN=" + escapeParam("InterviewPrep") + ":MAILTO:no-reply@interviewprep.local");
    }

    private void addAttendee(List<String> lines, User user, String role) {
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            return;
        }
        lines.add("ATTENDEE;CN=" + escapeParam(displayName(user))
                + ";ROLE=" + role
                + ";PARTSTAT=NEEDS-ACTION;RSVP=FALSE:MAILTO:" + user.getEmail().trim());
    }

    private String dashboardUrl(Session session) {
        String base = frontendUrl == null || frontendUrl.isBlank() ? "http://localhost:5500" : frontendUrl.replaceAll("/+$", "");
        return base + "/pages/dashboard.html#/sessions" + (session == null || session.getId() == null ? "" : "?sessionId=" + session.getId());
    }

    private String displayName(User user) {
        if (user == null) {
            return "InterviewPrep user";
        }
        String name = user.getName();
        if (name == null || name.isBlank()) {
            name = user.getUsername();
        }
        if (name == null || name.isBlank()) {
            name = user.getEmail();
        }
        return name == null || name.isBlank() ? "InterviewPrep user" : name;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String safeFileName(String value) {
        return value == null ? "interviewprep-session.ics" : value.replaceAll("[^A-Za-z0-9._-]", "-");
    }

    private String escapeText(String value) {
        return safe(value)
                .replace("\\", "\\\\")
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .replace("\n", "\\n")
                .replace(",", "\\,")
                .replace(";", "\\;");
    }

    private String escapeParam(String value) {
        return "\"" + safe(value).replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private String fold(String content) {
        StringBuilder folded = new StringBuilder();
        for (String line : content.split("\r\n", -1)) {
            while (line.length() > 73) {
                folded.append(line, 0, 73).append("\r\n ");
                line = line.substring(73);
            }
            folded.append(line).append("\r\n");
        }
        return folded.toString();
    }

    public enum CalendarInviteType {
        REQUEST,
        UPDATE,
        CANCEL
    }

    public record CalendarInvite(String filename, String method, String content) {
    }
}
