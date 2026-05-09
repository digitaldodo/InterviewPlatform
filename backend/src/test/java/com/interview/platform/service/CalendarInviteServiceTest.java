package com.interview.platform.service;

import com.interview.platform.model.Session;
import com.interview.platform.model.User;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CalendarInviteServiceTest {

    @Test
    void createsTimezoneSafeRequestInviteWithMeetingDetails() {
        CalendarInviteService service = service();
        Session session = session("CONFIRMED");
        User interviewer = user("int-1", "Interviewer", "interviewer@example.com", "America/New_York");
        User interviewee = user("cand-1", "Candidate", "candidate@example.com", "Asia/Kolkata");

        CalendarInviteService.CalendarInvite invite = service.sessionInvite(
                session,
                interviewer,
                interviewee,
                interviewee,
                "https://meet.jit.si/session-1",
                CalendarInviteService.CalendarInviteType.REQUEST
        );

        assertTrue(invite.filename().endsWith(".ics"));
        assertTrue(invite.content().contains("METHOD:REQUEST"));
        assertTrue(invite.content().contains("UID:interviewprep-session-session-1@interviewprep"));
        assertTrue(invite.content().contains("DTSTART:20260511T100000Z"));
        assertTrue(invite.content().contains("DTEND:20260511T110000Z"));
        assertTrue(invite.content().contains("SUMMARY:InterviewPrep: System Design"));
        assertTrue(invite.content().contains("LOCATION:https://meet.jit.si/session-1"));
        assertTrue(invite.content().contains("X-WR-TIMEZONE:Asia/Kolkata"));
        assertTrue(invite.content().contains("Topics: System Design\\, Java"));
    }

    @Test
    void createsCancellationInviteWithCalendarCancelMethod() {
        CalendarInviteService service = service();
        Session session = session("CANCELLED");
        User interviewer = user("int-1", "Interviewer", "interviewer@example.com", "UTC");
        User interviewee = user("cand-1", "Candidate", "candidate@example.com", "UTC");

        CalendarInviteService.CalendarInvite invite = service.sessionInvite(
                session,
                interviewer,
                interviewee,
                interviewee,
                "https://meet.jit.si/session-1",
                CalendarInviteService.CalendarInviteType.CANCEL
        );

        assertTrue(invite.content().contains("METHOD:CANCEL"));
        assertTrue(invite.content().contains("STATUS:CANCELLED"));
        assertTrue(invite.content().contains("SEQUENCE:2"));
        assertTrue(invite.content().contains("InterviewPrep cancellation notice"));
    }

    private CalendarInviteService service() {
        SchedulingTimeService schedulingTimeService = new SchedulingTimeService(
                ZoneId.of("UTC"),
                Clock.fixed(Instant.parse("2026-05-09T12:00:00Z"), ZoneId.of("UTC"))
        );
        return new CalendarInviteService(schedulingTimeService, "https://interviewprep.example");
    }

    private Session session(String status) {
        Session session = new Session();
        session.setId("session-1");
        session.setTitle("System Design");
        session.setTopics(List.of("System Design", "Java"));
        session.setInterviewerId("int-1");
        session.setCandidateId("cand-1");
        session.setStartTime("2026-05-11T10:00:00Z");
        session.setDurationMinutes(60);
        session.setStatus(status);
        session.setCreatedAt(Instant.parse("2026-05-09T10:00:00Z"));
        session.setUpdatedAt(Instant.parse("2026-05-09T10:00:00Z"));
        return session;
    }

    private User user(String id, String name, String email, String timezone) {
        User user = new User();
        user.setId(id);
        user.setName(name);
        user.setEmail(email);
        user.setTimeZone(timezone);
        return user;
    }
}
