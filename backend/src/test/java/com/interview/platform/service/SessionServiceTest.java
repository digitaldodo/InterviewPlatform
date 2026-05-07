package com.interview.platform.service;

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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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
    private MeetingProviderService meetingProviderService;
    @Mock
    private AvailabilitySlotService availabilitySlotService;

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
