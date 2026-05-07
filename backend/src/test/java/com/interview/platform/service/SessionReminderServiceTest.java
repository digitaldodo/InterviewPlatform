package com.interview.platform.service;

import com.interview.platform.exception.EmailDeliveryException;
import com.interview.platform.model.Session;
import com.interview.platform.model.User;
import com.interview.platform.repository.SessionRepository;
import com.interview.platform.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SessionReminderServiceTest {

    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EmailService emailService;
    @Mock
    private MongoTemplate mongoTemplate;

    private SessionReminderService reminderService;

    @BeforeEach
    void setUp() {
        SchedulingTimeService schedulingTimeService = new SchedulingTimeService(
                ZoneId.of("UTC"),
                Clock.fixed(Instant.parse("2026-05-07T12:00:00Z"), ZoneId.of("UTC"))
        );
        reminderService = new SessionReminderService(
                sessionRepository,
                userRepository,
                emailService,
                schedulingTimeService,
                mongoTemplate,
                true,
                30,
                100,
                5
        );
    }

    @Test
    void sendsReminderToBothParticipantsOnceWhenDue() {
        Session session = dueSession();
        User interviewer = user("int-1", "Interviewer", "interviewer@example.com");
        User interviewee = user("cand-1", "Candidate", "candidate@example.com");

        when(sessionRepository.findByStatusAndPreInterviewReminderSentAtIsNull(eq("CONFIRMED"), any(Pageable.class)))
                .thenReturn(List.of(session));
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Session.class)))
                .thenReturn(session);
        when(userRepository.findById("int-1")).thenReturn(Optional.of(interviewer));
        when(userRepository.findById("cand-1")).thenReturn(Optional.of(interviewee));

        reminderService.sendDuePreInterviewReminders();

        verify(emailService).sendPreInterviewReminder(
                eq("candidate@example.com"),
                eq("Candidate"),
                eq("Interviewer"),
                eq("Candidate"),
                eq(List.of("System Design", "Java")),
                eq("May 7, 2026"),
                eq("12:30 PM"),
                eq("UTC"),
                eq("https://join.example.com"),
                eq(60)
        );
        verify(emailService).sendPreInterviewReminder(
                eq("interviewer@example.com"),
                eq("Interviewer"),
                eq("Interviewer"),
                eq("Candidate"),
                eq(List.of("System Design", "Java")),
                eq("May 7, 2026"),
                eq("12:30 PM"),
                eq("UTC"),
                eq("https://host.example.com"),
                eq(60)
        );
        verify(mongoTemplate, atLeastOnce()).updateFirst(any(Query.class), any(Update.class), eq(Session.class));
    }

    @Test
    void skipsSessionsThatAreNotDueYet() {
        Session session = dueSession();
        session.setStartTime("2026-05-07T12:45:00Z");

        when(sessionRepository.findByStatusAndPreInterviewReminderSentAtIsNull(eq("CONFIRMED"), any(Pageable.class)))
                .thenReturn(List.of(session));

        reminderService.sendDuePreInterviewReminders();

        verify(mongoTemplate, never()).findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Session.class));
        verify(emailService, never()).sendPreInterviewReminder(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void doesNotResendRecipientReminderThatAlreadyHasTimestamp() {
        Session session = dueSession();
        session.setIntervieweeReminderSentAt(Instant.parse("2026-05-07T11:59:00Z"));
        User interviewer = user("int-1", "Interviewer", "interviewer@example.com");
        User interviewee = user("cand-1", "Candidate", "candidate@example.com");

        when(sessionRepository.findByStatusAndPreInterviewReminderSentAtIsNull(eq("CONFIRMED"), any(Pageable.class)))
                .thenReturn(List.of(session));
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Session.class)))
                .thenReturn(session);
        when(userRepository.findById("int-1")).thenReturn(Optional.of(interviewer));
        when(userRepository.findById("cand-1")).thenReturn(Optional.of(interviewee));

        reminderService.sendDuePreInterviewReminders();

        verify(emailService, never()).sendPreInterviewReminder(
                eq("candidate@example.com"),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        );
        verify(emailService).sendPreInterviewReminder(
                eq("interviewer@example.com"),
                eq("Interviewer"),
                eq("Interviewer"),
                eq("Candidate"),
                eq(List.of("System Design", "Java")),
                eq("May 7, 2026"),
                eq("12:30 PM"),
                eq("UTC"),
                eq("https://host.example.com"),
                eq(60)
        );
    }

    @Test
    void emailFailureDoesNotCrashScheduler() {
        Session session = dueSession();
        User interviewer = user("int-1", "Interviewer", "interviewer@example.com");
        User interviewee = user("cand-1", "Candidate", "candidate@example.com");

        when(sessionRepository.findByStatusAndPreInterviewReminderSentAtIsNull(eq("CONFIRMED"), any(Pageable.class)))
                .thenReturn(List.of(session));
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Session.class)))
                .thenReturn(session);
        when(userRepository.findById("int-1")).thenReturn(Optional.of(interviewer));
        when(userRepository.findById("cand-1")).thenReturn(Optional.of(interviewee));
        doThrow(new EmailDeliveryException("send failed"))
                .when(emailService)
                .sendPreInterviewReminder(eq("candidate@example.com"), any(), any(), any(), any(), any(), any(), any(), any(), any());

        assertDoesNotThrow(() -> reminderService.sendDuePreInterviewReminders());
    }

    private Session dueSession() {
        Session session = new Session();
        session.setId("session-1");
        session.setStatus("CONFIRMED");
        session.setInterviewerId("int-1");
        session.setCandidateId("cand-1");
        session.setTopics(List.of("System Design", "Java"));
        session.setStartTime("2026-05-07T12:30:00Z");
        session.setDurationMinutes(60);
        session.setJoinUrl("https://join.example.com");
        session.setHostUrl("https://host.example.com");
        return session;
    }

    private User user(String id, String name, String email) {
        User user = new User();
        user.setId(id);
        user.setName(name);
        user.setEmail(email);
        return user;
    }
}
