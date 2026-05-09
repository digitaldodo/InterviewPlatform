package com.interview.platform.service;

import com.interview.platform.dto.MeetingDtos;
import com.interview.platform.exception.UnauthorizedException;
import com.interview.platform.model.Session;
import com.interview.platform.model.User;
import com.interview.platform.exception.ResourceNotFoundException;
import com.interview.platform.repository.SessionRepository;
import com.interview.platform.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
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
    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    private static final Set<String> VALID_STATUSES = new HashSet<>(Arrays.asList(
            "PENDING", "CONFIRMED", "COMPLETED", "CANCELLED"
    ));
    private static final Duration HOST_JOIN_EARLY = Duration.ofMinutes(15);
    private static final Duration PARTICIPANT_JOIN_EARLY = Duration.ofMinutes(5);
    private static final Duration ACCESS_GRACE_AFTER_START = Duration.ofHours(4);
    private static final Duration COMPLETE_EARLY_WINDOW = Duration.ofMinutes(5);
    private static final Duration COMPLETE_GRACE_AFTER_END = Duration.ofMinutes(60);

    private final SessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final CalendarInviteService calendarInviteService;
    private final CalendarIntegrationService calendarIntegrationService;
    private final MeetingProviderService meetingProviderService;
    private final AvailabilitySlotService availabilitySlotService;
    private final CacheInvalidationService cacheInvalidationService;

    public SessionService(SessionRepository sessionRepository, UserRepository userRepository,
                          NotificationService notificationService, EmailService emailService,
                          CalendarInviteService calendarInviteService,
                          CalendarIntegrationService calendarIntegrationService,
                          MeetingProviderService meetingProviderService,
                          AvailabilitySlotService availabilitySlotService,
                          CacheInvalidationService cacheInvalidationService) {
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.emailService = emailService;
        this.calendarInviteService = calendarInviteService;
        this.calendarIntegrationService = calendarIntegrationService;
        this.meetingProviderService = meetingProviderService;
        this.availabilitySlotService = availabilitySlotService;
        this.cacheInvalidationService = cacheInvalidationService;
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
        if (!availabilitySlotService.hasStructuredAvailability(interviewer.getId())) {
            throw new IllegalArgumentException("This interviewer has not added availability yet");
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
        if (session.getMeetingProvider() == null || session.getMeetingProvider().isBlank()) {
            session.setMeetingProvider(interviewee.getPreferredMeetingProvider());
        }
        preventConflictingBooking(session.getInterviewerId(), session.getStartTime(), session.getDurationMinutes(), null);
        preventDuplicateCandidateBooking(session.getCandidateId(), session.getInterviewerId(), session.getStartTime(), session.getDurationMinutes(), null);
        meetingProviderService.provision(session, interviewer, interviewee);
        session.setCreatedAt(Instant.now());
        session.setUpdatedAt(Instant.now());

        Session saved = sessionRepository.save(session);
        cacheInvalidationService.evictAvailabilityCaches(saved.getInterviewerId());
        cacheInvalidationService.evictAnalyticsForParticipants(saved.getInterviewerId(), saved.getCandidateId());
        cacheInvalidationService.evictInterviewerCaches(saved.getInterviewerId(), interviewer.getUsername());
        notifyCreated(saved, interviewer, interviewee);
        calendarIntegrationService.syncSessionForParticipantsSafely(saved);
        return sanitizeSession(saved);
    }

    public List<Session> getAllSessions() {
        return sortSessions(sessionRepository.findAll()).stream()
                .map(this::sanitizeSession)
                .toList();
    }

    public List<Session> getSessionsForUser(User actor) {
        if (actor == null || actor.getId() == null || actor.getId().isBlank()) {
            throw new UnauthorizedException("Authentication required");
        }
        return sortSessions(sessionRepository.findByInterviewerIdOrCandidateId(actor.getId(), actor.getId())).stream()
                .map(this::sanitizeSession)
                .toList();
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
        return sortSessions(sessionRepository.findByInterviewerId(interviewerId)).stream()
                .map(this::sanitizeSession)
                .toList();
    }

    public List<Session> getByIntervieweeId(String intervieweeId, User actor) {
        if (!isAdmin(actor) && !actor.getId().equals(intervieweeId)) {
            throw new UnauthorizedException("You can only view your own interview sessions");
        }
        return sortSessions(sessionRepository.findByCandidateId(intervieweeId)).stream()
                .map(this::sanitizeSession)
                .toList();
    }

    public Session getByIdForUser(String id, User actor) {
        return sanitizeSession(requireParticipant(id, actor));
    }

    public Session rescheduleSession(String id, String startTime, Integer durationMinutes, User actor) {
        Session session = requireParticipant(id, actor);
        String status = normalizeStatusSafe(session.getStatus());
        if (!"PENDING".equals(status) && !"CONFIRMED".equals(status)) {
            throw new IllegalArgumentException("Only pending or confirmed sessions can be rescheduled");
        }
        if (startTime == null || startTime.isBlank()) {
            throw new IllegalArgumentException("Start time is required");
        }
        User interviewer = userRepository.findById(session.getInterviewerId())
                .orElseThrow(() -> new ResourceNotFoundException("Interviewer not found"));
        User interviewee = userRepository.findById(session.getCandidateId())
                .orElseThrow(() -> new ResourceNotFoundException("Interviewee not found"));
        AvailabilitySlotService.BookingResolution bookingResolution = availabilitySlotService.resolveRequestedBooking(
                session.getInterviewerId(),
                startTime,
                durationMinutes == null ? session.getDurationMinutes() : durationMinutes
        );
        preventConflictingBooking(session.getInterviewerId(), bookingResolution.startTime(), bookingResolution.durationMinutes(), session.getId());
        preventDuplicateCandidateBooking(session.getCandidateId(), session.getInterviewerId(), bookingResolution.startTime(), bookingResolution.durationMinutes(), session.getId());
        String oldStart = session.getStartTime();
        session.setStartTime(bookingResolution.startTime());
        session.setDurationMinutes(bookingResolution.durationMinutes());
        session.setMeetingStatus("SCHEDULED");
        session.setRescheduledAt(Instant.now());
        session.setUpdatedAt(Instant.now());
        session.setReminderDispatchKeys(new java.util.ArrayList<>());
        session.setPreInterviewReminderSentAt(null);
        session.setPreInterviewReminderClaimedAt(null);
        session.setInterviewerReminderSentAt(null);
        session.setIntervieweeReminderSentAt(null);
        ensureMeetingProvisioned(session, interviewer, interviewee);
        Session saved = sessionRepository.save(session);
        cacheInvalidationService.evictAvailabilityCaches(saved.getInterviewerId());
        cacheInvalidationService.evictAnalyticsForParticipants(saved.getInterviewerId(), saved.getCandidateId());
        notifyRescheduled(saved, oldStart, interviewer, interviewee);
        calendarIntegrationService.syncSessionForParticipantsSafely(saved);
        return sanitizeSession(saved);
    }

    public Session updateSessionStatus(String id, String status, User actor) {
        Session session = requireParticipant(id, actor);
        String normalizedStatus = normalizeStatus(status);
        if ("CONFIRMED".equals(normalizedStatus) && !actor.getId().equals(session.getInterviewerId())) {
            throw new UnauthorizedException("Only the assigned interviewer can confirm the session");
        }
        if ("COMPLETED".equals(normalizedStatus)
                && "COMPLETED".equals(normalizeStatusSafe(session.getStatus()))
                && "COMPLETED".equals(normalizeStatusSafe(session.getMeetingStatus()))) {
            return sanitizeSession(session);
        }
        if ("COMPLETED".equals(normalizedStatus)) {
            validateCompletionWindow(session);
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
            session.setCancelledAt(Instant.now());
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
        cacheInvalidationService.evictAvailabilityCaches(saved.getInterviewerId());
        cacheInvalidationService.evictAnalyticsForParticipants(saved.getInterviewerId(), saved.getCandidateId());
        notifyStatusChanged(saved);
        calendarIntegrationService.syncSessionForParticipantsSafely(saved);
        return sanitizeSession(saved);
    }

    public List<MeetingDtos.MeetingProviderOption> getMeetingProviderOptions() {
        return meetingProviderService.providerOptions();
    }

    public CalendarInviteService.CalendarInvite calendarInviteForSession(String sessionId, User actor) {
        Session session = requireParticipant(sessionId, actor);
        User interviewer = userRepository.findById(session.getInterviewerId())
                .orElseThrow(() -> new ResourceNotFoundException("Interviewer not found"));
        User interviewee = userRepository.findById(session.getCandidateId())
                .orElseThrow(() -> new ResourceNotFoundException("Interviewee not found"));
        User recipient = actor != null && actor.getId() != null && actor.getId().equals(interviewer.getId()) ? interviewer : interviewee;
        String meetingLink = actor != null && actor.getId() != null && actor.getId().equals(interviewer.getId())
                ? interviewerMeetingLink(session)
                : session.getJoinUrl();
        CalendarInviteService.CalendarInviteType type = "CANCELLED".equals(normalizeStatusSafe(session.getStatus()))
                ? CalendarInviteService.CalendarInviteType.CANCEL
                : CalendarInviteService.CalendarInviteType.UPDATE;
        return calendarInviteService.sessionInvite(session, interviewer, interviewee, recipient, meetingLink, type);
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
        return securedMeetingAccess(session, actor, interviewer, interviewee, false);
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
            cacheInvalidationService.evictAnalyticsForParticipants(session.getInterviewerId(), session.getCandidateId());
            notifyMeetingStarted(session);
        }
        return securedMeetingAccess(session, actor, interviewer, interviewee, true);
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

    private MeetingDtos.MeetingAccessResponse securedMeetingAccess(Session session, User actor, User interviewer, User interviewee,
                                                                  boolean hostTriggeredStart) {
        MeetingDtos.MeetingAccessResponse base = meetingProviderService.buildAccess(session, actor, interviewer, interviewee);
        MeetingDtos.MeetingSessionState state = buildMeetingState(session, actor, hostTriggeredStart);
        if (state.sensitiveAccessExposed()) {
            return base.withState(state);
        }
        return base.withoutSensitiveAccess(state);
    }

    private MeetingDtos.MeetingSessionState buildMeetingState(Session session, User actor, boolean hostTriggeredStart) {
        Instant now = Instant.now();
        String normalizedSessionStatus = normalizeStatusSafe(session.getStatus());
        String normalizedMeetingStatus = normalizeStatusSafe(session.getMeetingStatus());
        Optional<Instant> scheduledStart = parseSessionTime(session.getStartTime());
        Instant start = scheduledStart.orElse(now);
        Instant hostJoinAt = start.minus(HOST_JOIN_EARLY);
        Instant participantJoinAt = start.minus(PARTICIPANT_JOIN_EARLY);
        Instant accessExpiresAt = start.plus(Duration.ofMinutes((long) effectiveDurationMinutes(session.getDurationMinutes())))
                .plus(ACCESS_GRACE_AFTER_START);
        boolean host = actor != null && actor.getId() != null && actor.getId().equals(session.getInterviewerId());
        boolean ended = "COMPLETED".equals(normalizedSessionStatus) || "CANCELLED".equals(normalizedSessionStatus)
                || "COMPLETED".equals(normalizedMeetingStatus) || "CANCELLED".equals(normalizedMeetingStatus);
        if (ended) {
            return new MeetingDtos.MeetingSessionState(
                    normalizedSessionStatus,
                    "ENDED",
                    "This interview session has ended.",
                    null,
                    accessExpiresAt,
                    false,
                    false,
                    false,
                    false
            );
        }
        if ("LIVE".equals(normalizedMeetingStatus)) {
            return new MeetingDtos.MeetingSessionState(
                    normalizedSessionStatus,
                    "LIVE",
                    "The interview is live. You can join or reconnect now.",
                    now,
                    accessExpiresAt,
                    host,
                    true,
                    false,
                    true
            );
        }
        if (host) {
            if (now.isBefore(hostJoinAt)) {
                return new MeetingDtos.MeetingSessionState(
                        normalizedSessionStatus,
                        "COUNTDOWN",
                        "The room opens 15 minutes before the scheduled start time.",
                        hostJoinAt,
                        accessExpiresAt,
                        false,
                        false,
                        false,
                        false
                );
            }
            return new MeetingDtos.MeetingSessionState(
                    normalizedSessionStatus,
                    "READY_TO_START",
                    "Start the room when you are ready. Interviewees will be admitted after you begin.",
                    now,
                    accessExpiresAt,
                    true,
                    false,
                    false,
                    false
            );
        }
        if (now.isBefore(participantJoinAt)) {
            return new MeetingDtos.MeetingSessionState(
                    normalizedSessionStatus,
                    "COUNTDOWN",
                    "Your interview lobby opens 5 minutes before the scheduled start time.",
                    participantJoinAt,
                    accessExpiresAt,
                    false,
                    false,
                    false,
                    false
            );
        }
        return new MeetingDtos.MeetingSessionState(
                normalizedSessionStatus,
                "WAITING_FOR_HOST",
                "The interviewer needs to start the interview before the room is unlocked.",
                participantJoinAt,
                accessExpiresAt,
                false,
                false,
                true,
                false
        );
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

    private void preventConflictingBooking(String interviewerId, String startTime, Integer durationMinutes, String excludedSessionId) {
        List<Session> activeSessions = sessionRepository.findByInterviewerIdAndStatusIn(interviewerId, List.of("PENDING", "CONFIRMED"));
        Optional<Instant> requestedStart = parseSessionTime(startTime);
        Instant requestedEnd = requestedStart
                .map(value -> value.plusSeconds((long) effectiveDurationMinutes(durationMinutes) * 60))
                .orElse(null);
        for (Session existing : activeSessions) {
            if (excludedSessionId != null && excludedSessionId.equals(existing.getId())) {
                continue;
            }
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

    private void preventDuplicateCandidateBooking(String candidateId, String interviewerId, String startTime, Integer durationMinutes, String excludedSessionId) {
        List<Session> activeSessions = sessionRepository.findByCandidateIdAndStatusIn(candidateId, List.of("PENDING", "CONFIRMED"));
        Optional<Instant> requestedStart = parseSessionTime(startTime);
        Instant requestedEnd = requestedStart
                .map(value -> value.plusSeconds((long) effectiveDurationMinutes(durationMinutes) * 60))
                .orElse(null);
        for (Session existing : activeSessions) {
            if (excludedSessionId != null && excludedSessionId.equals(existing.getId())) {
                continue;
            }
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

    private void validateCompletionWindow(Session session) {
        String currentStatus = normalizeStatusSafe(session.getStatus());
        String meetingState = normalizeStatusSafe(session.getMeetingStatus());
        if ("COMPLETED".equals(currentStatus) && "COMPLETED".equals(meetingState)) {
            return;
        }
        if (!"CONFIRMED".equals(currentStatus)) {
            throw new IllegalArgumentException("Only confirmed sessions can be completed");
        }
        if ("CANCELLED".equals(meetingState) || "COMPLETED".equals(meetingState)) {
            throw new IllegalArgumentException("This meeting can no longer be completed");
        }
        Instant scheduledStart = parseSessionTime(session.getStartTime())
                .orElseThrow(() -> new IllegalArgumentException("Session start time is invalid"));
        Instant now = Instant.now();
        Instant opensAt = scheduledStart.minus(COMPLETE_EARLY_WINDOW);
        Instant closesAt = scheduledStart
                .plus(Duration.ofMinutes((long) effectiveDurationMinutes(session.getDurationMinutes())))
                .plus(COMPLETE_GRACE_AFTER_END);
        if (now.isBefore(opensAt)) {
            throw new IllegalArgumentException("Interview can only be completed near the scheduled meeting window");
        }
        if (now.isAfter(closesAt)) {
            throw new IllegalArgumentException("Interview completion window has expired");
        }
        if (session.getMeetingStartedAt() != null && now.isBefore(session.getMeetingStartedAt().minus(Duration.ofMinutes(1)))) {
            throw new IllegalArgumentException("Interview cannot be completed before the meeting starts");
        }
    }

    private void notifyCreated(Session session, User interviewer, User interviewee) {
        notificationService.create(session.getCandidateId(), "SESSION_SCHEDULED", "Booking requested",
                "Your " + session.getTitle() + " session request was sent.");
        notificationService.create(session.getInterviewerId(), "SESSION_REQUESTED", "New interview request",
                "You have a new " + session.getTitle() + " interview request.");
        safeEmail(() -> emailService.sendSessionInvite(
                interviewee.getEmail(),
                interviewee.getName(),
                interviewer.getName(),
                session.getTitle(),
                session.getStartTime(),
                session.getMeetingProvider(),
                session.getJoinUrl(),
                interviewee.getTimeZone(),
                calendarInviteService.sessionInvite(
                        session,
                        interviewer,
                        interviewee,
                        interviewee,
                        session.getJoinUrl(),
                        CalendarInviteService.CalendarInviteType.REQUEST
                )
        ), "candidate booking invite", session);
        safeEmail(() -> emailService.sendSessionInvite(
                interviewer.getEmail(),
                interviewer.getName(),
                interviewee.getName(),
                session.getTitle(),
                session.getStartTime(),
                session.getMeetingProvider(),
                interviewerMeetingLink(session),
                interviewer.getTimeZone(),
                calendarInviteService.sessionInvite(
                        session,
                        interviewer,
                        interviewee,
                        interviewer,
                        interviewerMeetingLink(session),
                        CalendarInviteService.CalendarInviteType.REQUEST
                )
        ), "interviewer booking invite", session);
    }

    private void notifyRescheduled(Session session, String oldStart, User interviewer, User interviewee) {
        notificationService.create(session.getCandidateId(), "SESSION_RESCHEDULED", "Interview rescheduled",
                "Your " + session.getTitle() + " session moved from " + oldStart + " to " + session.getStartTime() + ".",
                java.util.Map.of("sessionId", session.getId(), "oldStartTime", oldStart, "startTime", session.getStartTime(), "route", "sessions"));
        notificationService.create(session.getInterviewerId(), "SESSION_RESCHEDULED", "Interview rescheduled",
                "The " + session.getTitle() + " session moved from " + oldStart + " to " + session.getStartTime() + ".",
                java.util.Map.of("sessionId", session.getId(), "oldStartTime", oldStart, "startTime", session.getStartTime(), "route", "sessions"));
        safeEmail(() -> emailService.sendSessionStatusUpdate(
                interviewee.getEmail(),
                session.getTitle(),
                "RESCHEDULED",
                session.getStartTime(),
                session.getJoinUrl(),
                interviewee.getTimeZone(),
                calendarInviteService.sessionInvite(session, interviewer, interviewee, interviewee, session.getJoinUrl(), CalendarInviteService.CalendarInviteType.UPDATE)
        ), "candidate reschedule update", session);
        safeEmail(() -> emailService.sendSessionStatusUpdate(
                interviewer.getEmail(),
                session.getTitle(),
                "RESCHEDULED",
                session.getStartTime(),
                interviewerMeetingLink(session),
                interviewer.getTimeZone(),
                calendarInviteService.sessionInvite(session, interviewer, interviewee, interviewer, interviewerMeetingLink(session), CalendarInviteService.CalendarInviteType.UPDATE)
        ), "interviewer reschedule update", session);
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
                        safeEmail(() -> emailService.sendSessionCancellation(
                                candidate.getEmail(),
                                session.getTitle(),
                                session.getStartTime(),
                                interviewer.getName(),
                                candidate.getTimeZone(),
                                calendarInviteService.sessionInvite(session, interviewer, candidate, candidate, session.getJoinUrl(), CalendarInviteService.CalendarInviteType.CANCEL)
                        ), "candidate cancellation", session);
                        safeEmail(() -> emailService.sendSessionCancellation(
                                interviewer.getEmail(),
                                session.getTitle(),
                                session.getStartTime(),
                                candidate.getName(),
                                interviewer.getTimeZone(),
                                calendarInviteService.sessionInvite(session, interviewer, candidate, interviewer, interviewerMeetingLink(session), CalendarInviteService.CalendarInviteType.CANCEL)
                        ), "interviewer cancellation", session);
                    } else {
                        safeEmail(() -> emailService.sendSessionStatusUpdate(
                                candidate.getEmail(),
                                session.getTitle(),
                                session.getStatus(),
                                session.getStartTime(),
                                session.getJoinUrl(),
                                candidate.getTimeZone(),
                                calendarInviteService.sessionInvite(session, interviewer, candidate, candidate, session.getJoinUrl(), CalendarInviteService.CalendarInviteType.UPDATE)
                        ), "candidate status update", session);
                        safeEmail(() -> emailService.sendSessionStatusUpdate(
                                interviewer.getEmail(),
                                session.getTitle(),
                                session.getStatus(),
                                session.getStartTime(),
                                interviewerMeetingLink(session),
                                interviewer.getTimeZone(),
                                calendarInviteService.sessionInvite(session, interviewer, candidate, interviewer, interviewerMeetingLink(session), CalendarInviteService.CalendarInviteType.UPDATE)
                        ), "interviewer status update", session);
                    }
                }));
    }

    private void notifyMeetingStarted(Session session) {
        notificationService.create(session.getCandidateId(), "MEETING_LIVE", "Interview is live",
                "Your " + session.getTitle() + " interview room is now live.",
                java.util.Map.of("sessionId", session.getId(), "startTime", session.getStartTime(), "route", "meeting"));
        notificationService.create(session.getInterviewerId(), "MEETING_LIVE", "Interview is live",
                "Your " + session.getTitle() + " interview room is now live.",
                java.util.Map.of("sessionId", session.getId(), "startTime", session.getStartTime(), "route", "meeting"));
        userRepository.findById(session.getCandidateId()).ifPresent(candidate ->
                userRepository.findById(session.getInterviewerId()).ifPresent(interviewer -> {
                    safeEmail(() -> emailService.sendMeetingReminder(candidate.getEmail(), session.getTitle(), session.getStartTime(), session.getJoinUrl(), interviewer.getName(), candidate.getTimeZone()), "candidate live meeting reminder", session);
                    safeEmail(() -> emailService.sendMeetingReminder(interviewer.getEmail(), session.getTitle(), session.getStartTime(),
                            session.getHostUrl() == null || session.getHostUrl().isBlank() ? session.getJoinUrl() : session.getHostUrl(),
                            candidate.getName(),
                            interviewer.getTimeZone()), "interviewer live meeting reminder", session);
                }));
    }

    private void safeEmail(Runnable action, String label, Session session) {
        try {
            action.run();
        } catch (RuntimeException ex) {
            String sessionId = session == null ? "unknown" : session.getId();
            log.warn("Email delivery failed safely for {} session {}: {}", label, sessionId, ex.getMessage());
        }
    }

    private List<Session> sortSessions(List<Session> items) {
        return items.stream()
                .sorted(Comparator.comparing(this::sessionSortValue))
                .toList();
    }

    private String interviewerMeetingLink(Session session) {
        return session.getHostUrl() == null || session.getHostUrl().isBlank()
                ? session.getJoinUrl()
                : session.getHostUrl();
    }

    private Session sanitizeSession(Session source) {
        if (source == null) {
            return null;
        }
        Session view = new Session();
        view.setId(source.getId());
        view.setTitle(source.getTitle());
        view.setInterviewerId(source.getInterviewerId());
        view.setCandidateId(source.getCandidateId());
        view.setStartTime(source.getStartTime());
        view.setStatus(source.getStatus());
        view.setNotes(source.getNotes());
        view.setInterviewType(source.getInterviewType());
        view.setTopics(source.getTopics());
        view.setDurationMinutes(source.getDurationMinutes());
        view.setMeetingProvider(source.getMeetingProvider());
        view.setMeetingStatus(source.getMeetingStatus());
        view.setMeetingStartedAt(source.getMeetingStartedAt());
        view.setMeetingEndedAt(source.getMeetingEndedAt());
        view.setPreInterviewReminderSentAt(source.getPreInterviewReminderSentAt());
        view.setPreInterviewReminderClaimedAt(source.getPreInterviewReminderClaimedAt());
        view.setInterviewerReminderSentAt(source.getInterviewerReminderSentAt());
        view.setIntervieweeReminderSentAt(source.getIntervieweeReminderSentAt());
        view.setReminderDispatchKeys(source.getReminderDispatchKeys());
        view.setLastReminderFailureAt(source.getLastReminderFailureAt());
        view.setLastReminderFailureMessage(source.getLastReminderFailureMessage());
        view.setRescheduledAt(source.getRescheduledAt());
        view.setCancelledAt(source.getCancelledAt());
        view.setCreatedAt(source.getCreatedAt());
        view.setUpdatedAt(source.getUpdatedAt());
        return view;
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
        User saved = userRepository.save(user);
        cacheInvalidationService.evictUserProfile(saved.getId());
        cacheInvalidationService.evictInterviewerCaches(saved.getId(), saved.getUsername());
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

