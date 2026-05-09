package com.interview.platform.service;

import com.interview.platform.dto.MeetingDtos;
import com.interview.platform.exception.UnauthorizedException;
import com.interview.platform.model.Session;
import com.interview.platform.model.User;
import com.interview.platform.repository.SessionRepository;
import com.interview.platform.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private EmailService emailService;
    @Mock
    private CalendarInviteService calendarInviteService;
    @Mock
    private CalendarIntegrationService calendarIntegrationService;
    @Mock
    private MeetingProviderService meetingProviderService;
    @Mock
    private AvailabilitySlotService availabilitySlotService;
    @Mock
    private CacheInvalidationService cacheInvalidationService;

    @InjectMocks
    private SessionService sessionService;

    @Test
    void createSessionRejectsSelfBooking() {
        Session session = new Session();
        session.setCandidateId("user-1");
        session.setInterviewerId("user-1");
        session.setTitle("System Design");
        session.setStartTime("2026-05-11T10:00:00Z");

        when(userRepository.existsById("user-1")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> sessionService.createSession(session));
        verify(meetingProviderService, never()).provision(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createSessionRejectsOverlappingExistingBooking() {
        Session request = new Session();
        request.setCandidateId("cand-1");
        request.setInterviewerId("int-1");
        request.setTitle("Behavioral");
        request.setInterviewType("Behavioral");
        request.setStartTime("2026-05-11T10:00:00Z");
        request.setDurationMinutes(60);

        Session existing = new Session();
        existing.setInterviewerId("int-1");
        existing.setStartTime("2026-05-11T10:30:00Z");
        existing.setDurationMinutes(60);
        existing.setStatus("CONFIRMED");

        User interviewer = interviewer("int-1");
        User candidate = candidate("cand-1");

        when(userRepository.existsById("int-1")).thenReturn(true);
        when(userRepository.existsById("cand-1")).thenReturn(true);
        when(userRepository.findById("int-1")).thenReturn(Optional.of(interviewer));
        when(userRepository.findById("cand-1")).thenReturn(Optional.of(candidate));
        when(availabilitySlotService.hasStructuredAvailability("int-1")).thenReturn(true);
        when(availabilitySlotService.resolveRequestedBooking("int-1", "2026-05-11T10:00:00Z", 60))
                .thenReturn(new AvailabilitySlotService.BookingResolution("2026-05-11T10:00:00Z", 60));
        when(sessionRepository.findByInterviewerIdAndStatusIn("int-1", List.of("PENDING", "CONFIRMED")))
                .thenReturn(List.of(existing));

        assertThrows(IllegalArgumentException.class, () -> sessionService.createSession(request));
        verify(meetingProviderService, never()).provision(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createSessionRejectsDuplicateCandidateBooking() {
        Session request = new Session();
        request.setCandidateId("cand-1");
        request.setInterviewerId("int-1");
        request.setTitle("Backend");
        request.setInterviewType("Backend");
        request.setStartTime("2026-05-11T10:00:00Z");
        request.setDurationMinutes(60);

        Session existing = new Session();
        existing.setCandidateId("cand-1");
        existing.setInterviewerId("int-1");
        existing.setStartTime("2026-05-11T10:00:00Z");
        existing.setDurationMinutes(60);
        existing.setStatus("PENDING");

        User interviewer = interviewer("int-1");
        User candidate = candidate("cand-1");

        when(userRepository.existsById("int-1")).thenReturn(true);
        when(userRepository.existsById("cand-1")).thenReturn(true);
        when(userRepository.findById("int-1")).thenReturn(Optional.of(interviewer));
        when(userRepository.findById("cand-1")).thenReturn(Optional.of(candidate));
        when(availabilitySlotService.hasStructuredAvailability("int-1")).thenReturn(true);
        when(availabilitySlotService.resolveRequestedBooking("int-1", "2026-05-11T10:00:00Z", 60))
                .thenReturn(new AvailabilitySlotService.BookingResolution("2026-05-11T10:00:00Z", 60));
        when(sessionRepository.findByInterviewerIdAndStatusIn("int-1", List.of("PENDING", "CONFIRMED")))
                .thenReturn(List.of());
        when(sessionRepository.findByCandidateIdAndStatusIn("cand-1", List.of("PENDING", "CONFIRMED")))
                .thenReturn(List.of(existing));

        assertThrows(IllegalArgumentException.class, () -> sessionService.createSession(request));
        verify(meetingProviderService, never()).provision(any(), any(), any());
    }

    @Test
    void createSessionRejectsWhenInterviewerIsNotAcceptingBookings() {
        Session request = new Session();
        request.setCandidateId("cand-1");
        request.setInterviewerId("int-1");
        request.setTitle("Backend");
        request.setInterviewType("Backend");
        request.setStartTime("2026-05-11T10:00:00Z");

        User interviewer = interviewer("int-1");
        interviewer.setAcceptingBookings(false);
        User candidate = candidate("cand-1");

        when(userRepository.existsById("int-1")).thenReturn(true);
        when(userRepository.existsById("cand-1")).thenReturn(true);
        when(userRepository.findById("int-1")).thenReturn(Optional.of(interviewer));
        when(userRepository.findById("cand-1")).thenReturn(Optional.of(candidate));

        assertThrows(IllegalArgumentException.class, () -> sessionService.createSession(request));
        verify(availabilitySlotService, never()).resolveRequestedBooking(any(), any(), any());
        verify(meetingProviderService, never()).provision(any(), any(), any());
    }

    @Test
    void createSessionRejectsWhenInterviewerHasNoAvailability() {
        Session request = new Session();
        request.setCandidateId("cand-1");
        request.setInterviewerId("int-1");
        request.setTitle("Backend");
        request.setInterviewType("Backend");
        request.setStartTime("2026-05-11T10:00:00Z");

        User interviewer = interviewer("int-1");
        User candidate = candidate("cand-1");

        when(userRepository.existsById("int-1")).thenReturn(true);
        when(userRepository.existsById("cand-1")).thenReturn(true);
        when(userRepository.findById("int-1")).thenReturn(Optional.of(interviewer));
        when(userRepository.findById("cand-1")).thenReturn(Optional.of(candidate));
        when(availabilitySlotService.hasStructuredAvailability("int-1")).thenReturn(false);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> sessionService.createSession(request));

        assertEquals("This interviewer has not added availability yet", ex.getMessage());
        verify(availabilitySlotService, never()).resolveRequestedBooking(any(), any(), any());
        verify(meetingProviderService, never()).provision(any(), any(), any());
    }

    @Test
    void createSessionRejectsWhenTargetIsNotInterviewerRole() {
        Session request = new Session();
        request.setCandidateId("cand-1");
        request.setInterviewerId("user-2");
        request.setTitle("Backend");
        request.setInterviewType("Backend");
        request.setStartTime("2026-05-11T10:00:00Z");

        User notInterviewer = candidate("user-2");
        User candidate = candidate("cand-1");

        when(userRepository.existsById("user-2")).thenReturn(true);
        when(userRepository.existsById("cand-1")).thenReturn(true);
        when(userRepository.findById("user-2")).thenReturn(Optional.of(notInterviewer));
        when(userRepository.findById("cand-1")).thenReturn(Optional.of(candidate));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> sessionService.createSession(request));
        assertEquals("Bookings can only be created with interviewer accounts", ex.getMessage());
        verify(meetingProviderService, never()).provision(any(), any(), any());
    }

    @Test
    void createSessionRejectsDisabledInterviewerAccount() {
        Session request = new Session();
        request.setCandidateId("cand-1");
        request.setInterviewerId("int-1");
        request.setTitle("Backend");
        request.setInterviewType("Backend");
        request.setStartTime("2026-05-11T10:00:00Z");

        User interviewer = interviewer("int-1");
        interviewer.setAccountEnabled(false);
        User candidate = candidate("cand-1");

        when(userRepository.existsById("int-1")).thenReturn(true);
        when(userRepository.existsById("cand-1")).thenReturn(true);
        when(userRepository.findById("int-1")).thenReturn(Optional.of(interviewer));
        when(userRepository.findById("cand-1")).thenReturn(Optional.of(candidate));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> sessionService.createSession(request));
        assertEquals("This interviewer account is disabled", ex.getMessage());
        verify(meetingProviderService, never()).provision(any(), any(), any());
    }

    @Test
    void getMeetingAccessRejectsNonParticipant() {
        Session session = session("session-1", "int-1", "cand-1", "CONFIRMED");
        User stranger = candidate("other-user");

        when(sessionRepository.findById("session-1")).thenReturn(Optional.of(session));

        assertThrows(UnauthorizedException.class, () -> sessionService.getMeetingAccess("session-1", stranger));
        verify(meetingProviderService, never()).buildAccess(any(), any(), any(), any());
    }

    @Test
    void getMeetingAccessRejectsPendingSessionUntilConfirmed() {
        Session session = session("session-1", "int-1", "cand-1", "PENDING");
        User candidate = candidate("cand-1");

        when(sessionRepository.findById("session-1")).thenReturn(Optional.of(session));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> sessionService.getMeetingAccess("session-1", candidate));

        assertEquals("Session must be confirmed before joining", ex.getMessage());
        verify(meetingProviderService, never()).buildAccess(any(), any(), any(), any());
    }

    @Test
    void startMeetingRequiresConfirmedSession() {
        Session session = session("session-1", "int-1", "cand-1", "PENDING");
        User interviewer = interviewer("int-1");

        when(sessionRepository.findById("session-1")).thenReturn(Optional.of(session));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> sessionService.startMeeting("session-1", interviewer));

        assertEquals("Approve the session before starting the meeting", ex.getMessage());
        verify(meetingProviderService, never()).provision(any(), any(), any());
    }

    @Test
    void startMeetingProvisionsMissingMeetingForConfirmedHost() {
        Session session = session("session-1", "int-1", "cand-1", "CONFIRMED");
        User interviewer = interviewer("int-1");
        User candidate = candidate("cand-1");
        MeetingDtos.MeetingAccessResponse access = new MeetingDtos.MeetingAccessResponse(
                "session-1",
                "Backend",
                "JITSI",
                "In-platform meeting",
                "room-1",
                "LIVE",
                "HOST",
                "Interviewer",
                "Candidate",
                "https://meet.jit.si/room-1",
                "https://meet.jit.si/room-1",
                "https://meet.jit.si/room-1",
                null,
                session.getStartTime(),
                60,
                null,
                null,
                true,
                false,
                "https://meet.jit.si/external_api.js",
                "meet.jit.si",
                "room-1",
                "Interviewer",
                "interviewer@example.com",
                null
        );

        when(sessionRepository.findById("session-1")).thenReturn(Optional.of(session));
        when(userRepository.findById("int-1")).thenReturn(Optional.of(interviewer));
        when(userRepository.findById("cand-1")).thenReturn(Optional.of(candidate));
        when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> invocation.getArgument(0));
        org.mockito.Mockito.doReturn(access)
                .when(meetingProviderService)
                .buildAccess(any(Session.class), any(User.class), any(User.class), any(User.class));

        MeetingDtos.MeetingAccessResponse result = sessionService.startMeeting("session-1", interviewer);

        assertEquals("room-1", result.meetingId());
        verify(meetingProviderService).provision(any(Session.class), any(User.class), any(User.class));
        verify(sessionRepository, times(2)).save(any(Session.class));
    }

    @Test
    void getMeetingAccessForIntervieweeWaitsForHostBeforeExposingRoom() {
        Session session = session("session-1", "int-1", "cand-1", "CONFIRMED");
        session.setStartTime(Instant.now().plusSeconds(120).toString());
        session.setMeetingProvider("JITSI");
        session.setMeetingId("room-1");
        User interviewer = interviewer("int-1");
        User candidate = candidate("cand-1");
        MeetingDtos.MeetingAccessResponse access = new MeetingDtos.MeetingAccessResponse(
                "session-1",
                "Backend",
                "JITSI",
                "In-platform meeting",
                "room-1",
                "SCHEDULED",
                "PARTICIPANT",
                "Interviewer",
                "Candidate",
                "https://meet.jit.si/room-1",
                "https://meet.jit.si/room-1",
                "https://meet.jit.si/room-1",
                null,
                session.getStartTime(),
                60,
                null,
                null,
                true,
                false,
                "https://meet.jit.si/external_api.js",
                "meet.jit.si",
                "room-1",
                "Candidate",
                "candidate@example.com",
                null
        );

        when(sessionRepository.findById("session-1")).thenReturn(Optional.of(session));
        when(userRepository.findById("int-1")).thenReturn(Optional.of(interviewer));
        when(userRepository.findById("cand-1")).thenReturn(Optional.of(candidate));
        when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> invocation.getArgument(0));
        org.mockito.Mockito.doReturn(access)
                .when(meetingProviderService)
                .buildAccess(nullable(Session.class), any(User.class), any(User.class), any(User.class));

        MeetingDtos.MeetingAccessResponse result = sessionService.getMeetingAccess("session-1", candidate);

        assertNull(result.joinUrl());
        assertNull(result.roomName());
        assertFalse(result.canEmbed());
        assertEquals("WAITING_FOR_HOST", result.sessionState().liveState());
        assertTrue(result.sessionState().waitingForHost());
        assertFalse(result.sessionState().sensitiveAccessExposed());
    }

    private Session session(String id, String interviewerId, String candidateId, String status) {
        Session session = new Session();
        session.setId(id);
        session.setInterviewerId(interviewerId);
        session.setCandidateId(candidateId);
        session.setTitle("Backend");
        session.setInterviewType("Backend");
        session.setStartTime("2026-05-11T10:00:00Z");
        session.setDurationMinutes(60);
        session.setStatus(status);
        return session;
    }

    private User interviewer(String id) {
        User user = new User();
        user.setId(id);
        user.setRole("INTERVIEWER");
        user.setEmail("interviewer@example.com");
        user.setUsername("Interviewer");
        return user;
    }

    private User candidate(String id) {
        User user = new User();
        user.setId(id);
        user.setRole("INTERVIEWEE");
        user.setEmail("candidate@example.com");
        user.setUsername("Candidate");
        return user;
    }
}
