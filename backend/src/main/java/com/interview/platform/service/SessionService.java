package com.interview.platform.service;

import com.interview.platform.dto.MeetingDtos;
import com.interview.platform.exception.UnauthorizedException;
import com.interview.platform.model.Session;
import com.interview.platform.model.User;
import com.interview.platform.exception.ResourceNotFoundException;
import com.interview.platform.repository.SessionRepository;
import com.interview.platform.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class SessionService {

    private static final Set<String> VALID_STATUSES = new HashSet<>(Arrays.asList(
            "PENDING", "CONFIRMED", "COMPLETED", "CANCELLED"
    ));

    private final SessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final MeetingProviderService meetingProviderService;
    private final AvailabilitySlotService availabilitySlotService;

    public SessionService(SessionRepository sessionRepository, UserRepository userRepository,
                          NotificationService notificationService, EmailService emailService,
                          MeetingProviderService meetingProviderService,
                          AvailabilitySlotService availabilitySlotService) {
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.emailService = emailService;
        this.meetingProviderService = meetingProviderService;
        this.availabilitySlotService = availabilitySlotService;
    }

    public Session createSession(Session session) {
        if (session == null) {
            throw new IllegalArgumentException("Session details are required");
        }
        if (session.getCandidateId() == null || session.getCandidateId().isBlank()) {
            throw new IllegalArgumentException("Interviewee ID is required");
        }
        List<String> topics = normalizeTopics(session.getTopics());
        if (topics.isEmpty() && session.getInterviewType() != null && !session.getInterviewType().isBlank()) {
            topics = normalizeTopics(List.of(session.getInterviewType()));
        }
        if (topics.isEmpty() && session.getTitle() != null && !session.getTitle().isBlank()) {
            topics = normalizeTopics(List.of(session.getTitle()));
        }
        if (topics.isEmpty()) {
            throw new IllegalArgumentException("At least one interview topic is required");
        }
        session.setTopics(topics);
        session.setTitle(String.join(", ", topics));
        session.setInterviewType(String.join(", ", topics));

        if (session.getStatus() == null) {
            session.setStatus("PENDING");
        } else {
            session.setStatus(normalizeStatus(session.getStatus()));
        }

        if (session.getInterviewerId() == null || session.getInterviewerId().isBlank()) {

            List<User> interviewers = userRepository.findByRole("INTERVIEWER").stream()
                    .filter(user -> !user.getId().equals(session.getCandidateId()))
                    .toList();

            if (!interviewers.isEmpty()) {
                // Preferred: find an interviewer whose skills overlap with selected topics.
                String topicText = String.join(" ", session.getTopics()).toLowerCase();

                Optional<User> skillMatch = interviewers.stream()
                        .filter(u -> u.getSkills() != null &&
                                     u.getSkills().stream()
                                             .anyMatch(skill -> topicText.contains(skill.toLowerCase())))
                        .findFirst();

                // Use skill-matched interviewer, or fall back to first available
                User assigned = skillMatch.orElse(interviewers.get(0));
                session.setInterviewerId(assigned.getId());
            }
        } else if (!userRepository.existsById(session.getInterviewerId())) {
            throw new IllegalArgumentException("Interviewer does not exist");
        }
        if (session.getInterviewerId() == null || session.getInterviewerId().isBlank()) {
            throw new IllegalArgumentException("Interviewer is required");
        }
        if (session.getInterviewerId() != null && session.getInterviewerId().equals(session.getCandidateId())) {
            throw new IllegalArgumentException("You cannot book yourself as interviewer");
        }

        if (!userRepository.existsById(session.getCandidateId())) {
            throw new IllegalArgumentException("Interviewee does not exist");
        }
        User interviewer = userRepository.findById(session.getInterviewerId())
                .orElseThrow(() -> new IllegalArgumentException("Interviewer does not exist"));
        User interviewee = userRepository.findById(session.getCandidateId())
                .orElseThrow(() -> new IllegalArgumentException("Interviewee does not exist"));
        validateBookingParticipants(interviewer, interviewee);
        if (!Boolean.TRUE.equals(interviewer.getAcceptingBookings())) {
            throw new IllegalArgumentException("This interviewer is not accepting bookings right now");
        }

        if (session.getStartTime() == null || session.getStartTime().isBlank()) {
            throw new IllegalArgumentException("Start time is required");
        }
        AvailabilitySlotService.BookingResolution bookingResolution = availabilitySlotService.resolveRequestedBooking(
                session.getInterviewerId(),
                session.getStartTime(),
                session.getDurationMinutes()
        );
        session.setStartTime(bookingResolution.startTime());
        session.setDurationMinutes(bookingResolution.durationMinutes());
        preventConflictingBooking(session.getInterviewerId(), session.getStartTime(), session.getDurationMinutes());
        preventDuplicateCandidateBooking(session.getCandidateId(), session.getInterviewerId(), session.getStartTime(), session.getDurationMinutes());
        meetingProviderService.provision(session, interviewer, interviewee);
        session.setCreatedAt(Instant.now());
        session.setUpdatedAt(Instant.now());

        Session saved = sessionRepository.save(session);
        notifyCreated(saved, interviewer, interviewee);
        return saved;
    }

    public List<Session> getAllSessions() {
        return sortSessions(sessionRepository.findAll());
    }

    public List<Session> getSessionsForUser(User actor) {
        if (actor == null || actor.getId() == null || actor.getId().isBlank()) {
            throw new UnauthorizedException("Authentication required");
        }
        return sortSessions(sessionRepository.findByInterviewerIdOrCandidateId(actor.getId(), actor.getId()));
    }

    public Optional<Session> getById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return sessionRepository.findById(id);
    }

    public List<Session> getByInterviewerId(String interviewerId, User actor) {
        if (!isAdmin(actor) && !actor.getId().equals(interviewerId)) {
            throw new UnauthorizedException("You can only view your own interviewer sessions");
        }
        return sortSessions(sessionRepository.findByInterviewerId(interviewerId));
    }

    public List<Session> getByIntervieweeId(String intervieweeId, User actor) {
        if (!isAdmin(actor) && !actor.getId().equals(intervieweeId)) {
            throw new UnauthorizedException("You can only view your own interview sessions");
        }
        return sortSessions(sessionRepository.findByCandidateId(intervieweeId));
    }

    public Session getByIdForUser(String id, User actor) {
        return requireParticipant(id, actor);
    }

    public Session updateSessionStatus(String id, String status, User actor) {
        Session session = requireParticipant(id, actor);
        String normalizedStatus = normalizeStatus(status);
        if ("CONFIRMED".equals(normalizedStatus) && !actor.getId().equals(session.getInterviewerId())) {
            throw new UnauthorizedException("Only the assigned interviewer can confirm the session");
        }
        session.setStatus(normalizedStatus);
        if ("COMPLETED".equals(normalizedStatus)) {
            session.setMeetingStatus("COMPLETED");
            if (session.getMeetingEndedAt() == null) {
                session.setMeetingEndedAt(Instant.now());
            }
        } else if ("CANCELLED".equals(normalizedStatus)) {
            session.setMeetingStatus("CANCELLED");
            if (session.getMeetingEndedAt() == null) {
                session.setMeetingEndedAt(Instant.now());
            }
        } else if ("CONFIRMED".equals(normalizedStatus)) {
            User interviewer = userRepository.findById(session.getInterviewerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Interviewer not found"));
            User interviewee = userRepository.findById(session.getCandidateId())
                    .orElseThrow(() -> new ResourceNotFoundException("Interviewee not found"));
            ensureMeetingProvisioned(session, interviewer, interviewee);
            if (session.getMeetingStatus() == null) {
                session.setMeetingStatus("SCHEDULED");
            }
        }
        session.setUpdatedAt(Instant.now());
        Session saved = sessionRepository.save(session);
        refreshUserSessionStats(saved.getInterviewerId());
        refreshUserSessionStats(saved.getCandidateId());
        notifyStatusChanged(saved);
        return saved;
    }

    public List<MeetingDtos.MeetingProviderOption> getMeetingProviderOptions() {
        return meetingProviderService.providerOptions();
    }

    public MeetingDtos.MeetingAccessResponse getMeetingAccess(String sessionId, User actor) {
        Session session = requireParticipant(sessionId, actor);
        String status = normalizeStatusSafe(session.getStatus());
        String meetingState = normalizeStatusSafe(session.getMeetingStatus());
        if ("PENDING".equals(status)) {
            throw new IllegalArgumentException("Session must be confirmed before joining");
        }
        if ("CANCELLED".equals(status) || "COMPLETED".equals(status) || "CANCELLED".equals(meetingState) || "COMPLETED".equals(meetingState)) {
            throw new IllegalArgumentException("This session can no longer be joined");
        }
        User interviewer = userRepository.findById(session.getInterviewerId()).orElseThrow(() -> new ResourceNotFoundException("Interviewer not found"));
        User interviewee = userRepository.findById(session.getCandidateId()).orElseThrow(() -> new ResourceNotFoundException("Interviewee not found"));
        session = ensureMeetingProvisioned(session, interviewer, interviewee);
        return meetingProviderService.buildAccess(session, actor, interviewer, interviewee);
    }

    public MeetingDtos.MeetingAccessResponse startMeeting(String sessionId, User actor) {
        Session session = requireParticipant(sessionId, actor);
        if (!actor.getId().equals(session.getInterviewerId())) {
            throw new UnauthorizedException("Only the assigned interviewer can start the meeting");
        }
        String meetingState = normalizeStatusSafe(session.getMeetingStatus());
        if ("CANCELLED".equals(normalizeStatusSafe(session.getStatus())) || "COMPLETED".equals(normalizeStatusSafe(session.getStatus()))
                || "CANCELLED".equals(meetingState) || "COMPLETED".equals(meetingState)) {
            throw new IllegalArgumentException("This session is no longer active");
        }
        if (!"CONFIRMED".equals(normalizeStatusSafe(session.getStatus()))) {
            throw new IllegalArgumentException("Approve the session before starting the meeting");
        }
        User interviewer = userRepository.findById(session.getInterviewerId()).orElseThrow(() -> new ResourceNotFoundException("Interviewer not found"));
        User interviewee = userRepository.findById(session.getCandidateId()).orElseThrow(() -> new ResourceNotFoundException("Interviewee not found"));
        session = ensureMeetingProvisioned(session, interviewer, interviewee);
        boolean wasLive = "LIVE".equals(normalizeStatusSafe(session.getMeetingStatus()));
        if (!wasLive) {
            session.setMeetingStatus("LIVE");
            session.setMeetingStartedAt(session.getMeetingStartedAt() == null ? Instant.now() : session.getMeetingStartedAt());
            session.setUpdatedAt(Instant.now());
            session = sessionRepository.save(session);
            notifyMeetingStarted(session);
        }
        return meetingProviderService.buildAccess(session, actor, interviewer, interviewee);
    }

    private Session ensureMeetingProvisioned(Session session, User interviewer, User interviewee) {
        if (session == null) {
            throw new ResourceNotFoundException("Session not found");
        }
        boolean missingMeeting = session.getMeetingProvider() == null || session.getMeetingProvider().isBlank()
                || session.getMeetingId() == null || session.getMeetingId().isBlank()
                || session.getJoinUrl() == null || session.getJoinUrl().isBlank();
        if (!missingMeeting) {
            return session;
        }
        meetingProviderService.provision(session, interviewer, interviewee);
        session.setUpdatedAt(Instant.now());
        return sessionRepository.save(session);
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("Status is required");
        }
        String normalized = status.trim().toUpperCase();
        if (!VALID_STATUSES.contains(normalized)) {
            throw new IllegalArgumentException("Status must be PENDING, CONFIRMED, COMPLETED, or CANCELLED");
        }
        return normalized;
    }

    private void preventConflictingBooking(String interviewerId, String startTime, Integer durationMinutes) {
        List<Session> activeSessions = sessionRepository.findByInterviewerIdAndStatusIn(interviewerId, List.of("PENDING", "CONFIRMED"));
        Optional<Instant> requestedStart = parseSessionTime(startTime);
        Instant requestedEnd = requestedStart
                .map(value -> value.plusSeconds((long) effectiveDurationMinutes(durationMinutes) * 60))
                .orElse(null);
        for (Session existing : activeSessions) {
            if (startTime.equals(existing.getStartTime())) {
                throw new IllegalArgumentException("That slot is no longer available");
            }
            Optional<Instant> existingStart = parseSessionTime(existing.getStartTime());
            if (requestedStart.isEmpty() || existingStart.isEmpty()) {
                continue;
            }
            Instant existingEnd = existingStart.get().plusSeconds((long) effectiveDurationMinutes(existing.getDurationMinutes()) * 60);
            boolean overlaps = requestedStart.get().isBefore(existingEnd) && requestedEnd.isAfter(existingStart.get());
            if (overlaps) {
                throw new IllegalArgumentException("That slot is no longer available");
            }
        }
    }

    private void preventDuplicateCandidateBooking(String candidateId, String interviewerId, String startTime, Integer durationMinutes) {
        List<Session> activeSessions = sessionRepository.findByCandidateIdAndStatusIn(candidateId, List.of("PENDING", "CONFIRMED"));
        Optional<Instant> requestedStart = parseSessionTime(startTime);
        Instant requestedEnd = requestedStart
                .map(value -> value.plusSeconds((long) effectiveDurationMinutes(durationMinutes) * 60))
                .orElse(null);
        for (Session existing : activeSessions) {
            if (interviewerId.equals(existing.getInterviewerId()) && startTime.equals(existing.getStartTime())) {
                throw new IllegalArgumentException("You have already booked this slot");
            }
            Optional<Instant> existingStart = parseSessionTime(existing.getStartTime());
            if (requestedStart.isEmpty() || existingStart.isEmpty()) {
                continue;
            }
            Instant existingEnd = existingStart.get().plusSeconds((long) effectiveDurationMinutes(existing.getDurationMinutes()) * 60);
            boolean overlaps = requestedStart.get().isBefore(existingEnd) && requestedEnd.isAfter(existingStart.get());
            if (overlaps) {
                throw new IllegalArgumentException("You already have another booking at that time");
            }
        }
    }

    private Session requireParticipant(String sessionId, User actor) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));
        if (!isAdmin(actor) && !actor.getId().equals(session.getCandidateId()) && !actor.getId().equals(session.getInterviewerId())) {
            throw new UnauthorizedException("You do not have access to this session");
        }
        return session;
    }

    private void notifyCreated(Session session, User interviewer, User interviewee) {
        notificationService.create(session.getCandidateId(), "SESSION_SCHEDULED", "Booking requested",
                "Your " + session.getTitle() + " session request was sent.");
        notificationService.create(session.getInterviewerId(), "SESSION_REQUESTED", "New interview request",
                "You have a new " + session.getTitle() + " interview request.");
        emailService.sendSessionInvite(
                interviewee.getEmail(),
                interviewee.getName(),
                interviewer.getName(),
                session.getTitle(),
                session.getStartTime(),
                session.getMeetingProvider(),
                session.getJoinUrl()
        );
        emailService.sendSessionInvite(
                interviewer.getEmail(),
                interviewer.getName(),
                interviewee.getName(),
                session.getTitle(),
                session.getStartTime(),
                session.getMeetingProvider(),
                session.getHostUrl() == null || session.getHostUrl().isBlank() ? session.getJoinUrl() : session.getHostUrl()
        );
    }

    private void notifyStatusChanged(Session session) {
        String title = "Session " + session.getStatus().toLowerCase();
        notificationService.create(session.getCandidateId(), "SESSION_" + session.getStatus(), title,
                "Your " + session.getTitle() + " session is now " + session.getStatus().toLowerCase() + ".");
        notificationService.create(session.getInterviewerId(), "SESSION_" + session.getStatus(), title,
                "The " + session.getTitle() + " session is now " + session.getStatus().toLowerCase() + ".");
        userRepository.findById(session.getCandidateId()).ifPresent(candidate ->
                userRepository.findById(session.getInterviewerId()).ifPresent(interviewer -> {
                    if ("CANCELLED".equals(normalizeStatusSafe(session.getStatus()))) {
                        emailService.sendSessionCancellation(candidate.getEmail(), session.getTitle(), session.getStartTime(), interviewer.getName());
                        emailService.sendSessionCancellation(interviewer.getEmail(), session.getTitle(), session.getStartTime(), candidate.getName());
                    } else {
                        emailService.sendSessionStatusUpdate(candidate.getEmail(), session.getTitle(), session.getStatus(), session.getStartTime(), session.getJoinUrl());
                        emailService.sendSessionStatusUpdate(interviewer.getEmail(), session.getTitle(), session.getStatus(), session.getStartTime(),
                                session.getHostUrl() == null || session.getHostUrl().isBlank() ? session.getJoinUrl() : session.getHostUrl());
                    }
                }));
    }

    private void notifyMeetingStarted(Session session) {
        notificationService.create(session.getCandidateId(), "MEETING_LIVE", "Interview is live",
                "Your " + session.getTitle() + " interview room is now live.");
        notificationService.create(session.getInterviewerId(), "MEETING_LIVE", "Interview is live",
                "Your " + session.getTitle() + " interview room is now live.");
        userRepository.findById(session.getCandidateId()).ifPresent(candidate ->
                userRepository.findById(session.getInterviewerId()).ifPresent(interviewer -> {
                    emailService.sendMeetingReminder(candidate.getEmail(), session.getTitle(), session.getStartTime(), session.getJoinUrl(), interviewer.getName());
                    emailService.sendMeetingReminder(interviewer.getEmail(), session.getTitle(), session.getStartTime(),
                            session.getHostUrl() == null || session.getHostUrl().isBlank() ? session.getJoinUrl() : session.getHostUrl(),
                            candidate.getName());
                }));
    }

    private List<Session> sortSessions(List<Session> items) {
        return items.stream()
                .sorted(Comparator.comparing(this::sessionSortValue))
                .toList();
    }

    private Instant sessionSortValue(Session session) {
        if (session == null || session.getStartTime() == null || session.getStartTime().isBlank()) {
            return Instant.MAX;
        }
        try {
            return OffsetDateTime.parse(session.getStartTime()).toInstant();
        } catch (DateTimeParseException ignored) {
            return Instant.MAX.minusSeconds(Math.abs(session.getStartTime().hashCode()));
        }
    }

    private String normalizeStatusSafe(String status) {
        if (status == null || status.isBlank()) {
            return "";
        }
        return status.trim().toUpperCase();
    }

    private Optional<Instant> parseSessionTime(String startTime) {
        if (startTime == null || startTime.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(OffsetDateTime.parse(startTime).toInstant());
        } catch (DateTimeParseException ignored) {
            try {
                return Optional.of(Instant.parse(startTime));
            } catch (DateTimeParseException secondIgnored) {
                return Optional.empty();
            }
        }
    }

    private int effectiveDurationMinutes(Integer durationMinutes) {
        return durationMinutes == null || durationMinutes <= 0 ? 45 : durationMinutes;
    }

    private List<String> normalizeTopics(List<String> values) {
        if (values == null) return List.of();
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .flatMap(value -> Arrays.stream(value.split(",")))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private void refreshUserSessionStats(String userId) {
        if (userId == null || userId.isBlank()) return;
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return;
        List<Session> sessions = sessionRepository.findByInterviewerIdOrCandidateId(userId, userId);
        long completed = sessions.stream().filter(session -> "COMPLETED".equalsIgnoreCase(session.getStatus())).count();
        long cancelled = sessions.stream().filter(session -> "CANCELLED".equalsIgnoreCase(session.getStatus())).count();
        user.setCompletedSessions((int) completed);
        user.setCancelledSessions((int) cancelled);
        if (user.hasRole("INTERVIEWER")) {
            long interviewerCompleted = sessions.stream()
                    .filter(session -> userId.equals(session.getInterviewerId()))
                    .filter(session -> "COMPLETED".equalsIgnoreCase(session.getStatus()))
                    .count();
            user.setCompletedInterviews((int) interviewerCompleted);
        }
        userRepository.save(user);
    }

    private boolean isAdmin(User actor) {
        return actor != null && actor.hasRole("ADMIN");
    }

    private void validateBookingParticipants(User interviewer, User interviewee) {
        if (interviewer == null || interviewee == null) {
            throw new IllegalArgumentException("Booking participants are required");
        }
        if (Boolean.FALSE.equals(interviewer.getAccountEnabled())) {
            throw new IllegalArgumentException("This interviewer account is disabled");
        }
        if (Boolean.FALSE.equals(interviewee.getAccountEnabled())) {
            throw new IllegalArgumentException("Your account is disabled");
        }
        if (!interviewer.hasRole("INTERVIEWER")) {
            throw new IllegalArgumentException("Bookings can only be created with interviewer accounts");
        }
        if (!interviewee.hasRole("INTERVIEWEE")) {
            throw new IllegalArgumentException("Only interviewee accounts can create bookings");
        }
    }
}

