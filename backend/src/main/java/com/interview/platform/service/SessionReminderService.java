package com.interview.platform.service;

import com.interview.platform.model.Session;
import com.interview.platform.model.User;
import com.interview.platform.repository.SessionRepository;
import com.interview.platform.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class SessionReminderService {
    private static final Logger log = LoggerFactory.getLogger(SessionReminderService.class);

    private static final String CONFIRMED = "CONFIRMED";
    private static final List<Integer> DEFAULT_OFFSETS = List.of(1440, 60, 30, 10);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    private final SessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final CalendarInviteService calendarInviteService;
    private final NotificationService notificationService;
    private final SchedulingTimeService schedulingTimeService;
    private final MongoTemplate mongoTemplate;
    private final boolean enabled;
    private final List<Integer> reminderOffsetsMinutes;
    private final int batchSize;
    private final Duration staleClaimAfter;
    private final String frontendUrl;
    private volatile Instant lastRunAt;
    private volatile Instant lastSuccessAt;
    private volatile Instant lastFailureAt;
    private volatile String lastFailureMessage;

    public SessionReminderService(SessionRepository sessionRepository,
                                  UserRepository userRepository,
                                  EmailService emailService,
                                  CalendarInviteService calendarInviteService,
                                  NotificationService notificationService,
                                  SchedulingTimeService schedulingTimeService,
                                  MongoTemplate mongoTemplate,
                                  @Value("${app.reminders.pre-interview.enabled:true}") boolean enabled,
                                  @Value("${app.reminders.pre-interview.minutes-before:30}") int minutesBefore,
                                  @Value("${app.reminders.pre-interview.batch-size:100}") int batchSize,
                                  @Value("${app.reminders.pre-interview.stale-claim-minutes:5}") int staleClaimMinutes,
                                  @Value("${app.frontend-url:http://localhost:5500}") String frontendUrl) {
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.calendarInviteService = calendarInviteService;
        this.notificationService = notificationService;
        this.schedulingTimeService = schedulingTimeService;
        this.mongoTemplate = mongoTemplate;
        this.enabled = enabled;
        this.reminderOffsetsMinutes = normalizeOffsets(minutesBefore);
        this.batchSize = Math.max(1, batchSize);
        this.staleClaimAfter = Duration.ofMinutes(Math.max(1, staleClaimMinutes));
        this.frontendUrl = frontendUrl == null || frontendUrl.isBlank() ? "http://localhost:5500" : frontendUrl.replaceAll("/+$", "");
    }

    @Scheduled(
            fixedRateString = "${app.reminders.pre-interview.check-rate-ms:60000}",
            initialDelayString = "${app.reminders.pre-interview.initial-delay-ms:15000}"
    )
    public void sendDuePreInterviewReminders() {
        if (!enabled) {
            return;
        }
        Instant now = schedulingTimeService.nowInstant();
        lastRunAt = now;
        try {
            sessionRepository.findByStatus(
                            CONFIRMED,
                            PageRequest.of(0, batchSize, Sort.by(Sort.Direction.ASC, "startTime"))
                    ).stream()
                    .filter(session -> isReminderDue(session, now))
                    .forEach(session -> sendReminderSafely(session, now));
            lastSuccessAt = now;
            lastFailureMessage = null;
        } catch (RuntimeException ex) {
            lastFailureAt = now;
            lastFailureMessage = ex.getMessage();
            log.error("Pre-interview reminder scheduler failed safely; next run will retry.", ex);
        }
    }

    private boolean isReminderDue(Session session, Instant now) {
        return dueOffset(session, now).isPresent();
    }

    private void sendReminderSafely(Session session, Instant now) {
        Optional<Session> claimed = claimSession(session.getId(), now);
        if (claimed.isEmpty()) {
            return;
        }
        try {
            Optional<Integer> offset = dueOffset(claimed.get(), now);
            if (offset.isEmpty()) {
                releaseClaim(claimed.get().getId());
                return;
            }
            sendReminder(claimed.get(), now, offset.get());
        } catch (RuntimeException ex) {
            releaseClaim(claimed.get().getId());
            log.error("Pre-interview reminder failed for session {}; it will be retried safely. reason={}",
                    claimed.get().getId(), ex.getMessage());
        }
    }

    private void sendReminder(Session session, Instant now, int minutesBeforeStart) {
        OffsetDateTime scheduled = schedulingTimeService.parseStartTime(session.getStartTime());
        User interviewer = userRepository.findById(session.getInterviewerId())
                .orElseThrow(() -> new IllegalStateException("Interviewer not found for session " + session.getId()));
        User interviewee = userRepository.findById(session.getCandidateId())
                .orElseThrow(() -> new IllegalStateException("Interviewee not found for session " + session.getId()));

        ZonedDateTime intervieweeTime = recipientSchedule(scheduled, interviewee);
        ZonedDateTime interviewerTime = recipientSchedule(scheduled, interviewer);
        String intervieweeDate = intervieweeTime.format(DATE_FORMATTER);
        String intervieweeClock = intervieweeTime.format(TIME_FORMATTER);
        String intervieweeTimezone = timezoneLabel(intervieweeTime);
        String interviewerDate = interviewerTime.format(DATE_FORMATTER);
        String interviewerClock = interviewerTime.format(TIME_FORMATTER);
        String interviewerTimezone = timezoneLabel(interviewerTime);

        String leadTimeLabel = leadTimeLabel(minutesBeforeStart);
        if (isRecipientReminderOpen(session, interviewee.getId(), minutesBeforeStart)) {
            boolean dispatched = false;
            if (interviewee.getReminderOffsetsMinutes().contains(minutesBeforeStart) && interviewee.getEmailRemindersEnabled()) {
                try {
                    emailService.sendPreInterviewReminder(
                    requireEmail(interviewee, "interviewee", session.getId()),
                    interviewee.getName(),
                    interviewer.getName(),
                    interviewee.getName(),
                    session.getTopics(),
                    intervieweeDate,
                    intervieweeClock,
                    intervieweeTimezone,
                    session.getJoinUrl(),
                    session.getDurationMinutes(),
                    leadTimeLabel,
                    calendarEventUrl(session),
                    calendarInviteService.sessionInvite(
                            session,
                            interviewer,
                            interviewee,
                            interviewee,
                            session.getJoinUrl(),
                            CalendarInviteService.CalendarInviteType.UPDATE
                    )
                    );
                    markDispatchKeySent(session.getId(), dispatchKey(interviewee.getId(), "EMAIL", minutesBeforeStart), now, null);
                    addDispatchKeyLocal(session, dispatchKey(interviewee.getId(), "EMAIL", minutesBeforeStart));
                    dispatched = true;
                } catch (RuntimeException ex) {
                    markReminderFailure(session.getId(), now, "Email reminder failed for interviewee: " + ex.getMessage());
                    log.warn("Pre-interview email reminder failed safely for session {} user {}: {}", session.getId(), interviewee.getId(), ex.getMessage());
                }
            }
            if (interviewee.getReminderOffsetsMinutes().contains(minutesBeforeStart) && interviewee.getInAppRemindersEnabled()) {
                notificationService.create(
                    session.getCandidateId(),
                    "SESSION_REMINDER",
                    "Interview starts in " + leadTimeLabel,
                    "Your " + session.getTitle() + " session starts at " + intervieweeClock + " (" + intervieweeTimezone + ").",
                    java.util.Map.of("sessionId", session.getId(), "startTime", session.getStartTime(), "route", "sessions", "minutesBefore", minutesBeforeStart)
                );
                markDispatchKeySent(session.getId(), dispatchKey(interviewee.getId(), "IN_APP", minutesBeforeStart), now, null);
                addDispatchKeyLocal(session, dispatchKey(interviewee.getId(), "IN_APP", minutesBeforeStart));
                dispatched = true;
            }
            if (minutesBeforeStart == 30 || session.getIntervieweeReminderSentAt() == null) {
                markRecipientReminderSent(session.getId(), "intervieweeReminderSentAt", now);
            }
            session.setIntervieweeReminderSentAt(now);
            if (!dispatched) {
                markDispatchKeySent(session.getId(), dispatchKey(interviewee.getId(), "DISABLED", minutesBeforeStart), now, null);
                addDispatchKeyLocal(session, dispatchKey(interviewee.getId(), "DISABLED", minutesBeforeStart));
            }
        }

        if (isRecipientReminderOpen(session, interviewer.getId(), minutesBeforeStart)) {
            boolean dispatched = false;
            if (interviewer.getReminderOffsetsMinutes().contains(minutesBeforeStart) && interviewer.getEmailRemindersEnabled()) {
                try {
                    emailService.sendPreInterviewReminder(
                    requireEmail(interviewer, "interviewer", session.getId()),
                    interviewer.getName(),
                    interviewer.getName(),
                    interviewee.getName(),
                    session.getTopics(),
                    interviewerDate,
                    interviewerClock,
                    interviewerTimezone,
                    interviewerMeetingLink(session),
                    session.getDurationMinutes(),
                    leadTimeLabel,
                    calendarEventUrl(session),
                    calendarInviteService.sessionInvite(
                            session,
                            interviewer,
                            interviewee,
                            interviewer,
                            interviewerMeetingLink(session),
                            CalendarInviteService.CalendarInviteType.UPDATE
                    )
                    );
                    markDispatchKeySent(session.getId(), dispatchKey(interviewer.getId(), "EMAIL", minutesBeforeStart), now, null);
                    addDispatchKeyLocal(session, dispatchKey(interviewer.getId(), "EMAIL", minutesBeforeStart));
                    dispatched = true;
                } catch (RuntimeException ex) {
                    markReminderFailure(session.getId(), now, "Email reminder failed for interviewer: " + ex.getMessage());
                    log.warn("Pre-interview email reminder failed safely for session {} user {}: {}", session.getId(), interviewer.getId(), ex.getMessage());
                }
            }
            if (interviewer.getReminderOffsetsMinutes().contains(minutesBeforeStart) && interviewer.getInAppRemindersEnabled()) {
                notificationService.create(
                    session.getInterviewerId(),
                    "SESSION_REMINDER",
                    "Interview starts in " + leadTimeLabel,
                    "Your " + session.getTitle() + " session starts at " + interviewerClock + " (" + interviewerTimezone + ").",
                    java.util.Map.of("sessionId", session.getId(), "startTime", session.getStartTime(), "route", "sessions", "minutesBefore", minutesBeforeStart)
                );
                markDispatchKeySent(session.getId(), dispatchKey(interviewer.getId(), "IN_APP", minutesBeforeStart), now, null);
                addDispatchKeyLocal(session, dispatchKey(interviewer.getId(), "IN_APP", minutesBeforeStart));
                dispatched = true;
            }
            if (minutesBeforeStart == 30 || session.getInterviewerReminderSentAt() == null) {
                markRecipientReminderSent(session.getId(), "interviewerReminderSentAt", now);
            }
            session.setInterviewerReminderSentAt(now);
            if (!dispatched) {
                markDispatchKeySent(session.getId(), dispatchKey(interviewer.getId(), "DISABLED", minutesBeforeStart), now, null);
                addDispatchKeyLocal(session, dispatchKey(interviewer.getId(), "DISABLED", minutesBeforeStart));
            }
        }

        if (!isReminderOpenForOffset(session, interviewer.getId(), interviewee.getId(), minutesBeforeStart)) {
            markSessionReminderComplete(session.getId(), now);
            log.info("{} pre-interview reminder processed for session {}", leadTimeLabel, session.getId());
        } else {
            releaseClaim(session.getId());
        }
    }

    public ReminderDiagnostics diagnostics() {
        return new ReminderDiagnostics(
                enabled,
                reminderOffsetsMinutes,
                batchSize,
                staleClaimAfter.toMinutes(),
                toString(lastRunAt),
                toString(lastSuccessAt),
                toString(lastFailureAt),
                lastFailureMessage
        );
    }

    private Optional<Session> claimSession(String sessionId, Instant now) {
        Instant staleBefore = now.minus(staleClaimAfter);
        Criteria claimIsAvailable = new Criteria().orOperator(
                Criteria.where("preInterviewReminderClaimedAt").is(null),
                Criteria.where("preInterviewReminderClaimedAt").lt(staleBefore)
        );
        Query query = Query.query(new Criteria().andOperator(
                Criteria.where("id").is(sessionId),
                Criteria.where("status").is(CONFIRMED),
                claimIsAvailable
        ));
        Update update = new Update()
                .set("preInterviewReminderClaimedAt", now)
                .set("updatedAt", now);
        Session claimed = mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                Session.class
        );
        return Optional.ofNullable(claimed);
    }

    private void markRecipientReminderSent(String sessionId, String fieldName, Instant sentAt) {
        Query query = Query.query(Criteria.where("id").is(sessionId).and(fieldName).is(null));
        Update update = new Update()
                .set(fieldName, sentAt)
                .set("updatedAt", sentAt);
        mongoTemplate.updateFirst(query, update, Session.class);
    }

    private void markSessionReminderComplete(String sessionId, Instant sentAt) {
        Query query = Query.query(Criteria.where("id").is(sessionId));
        Update update = new Update()
                .set("preInterviewReminderSentAt", sentAt)
                .unset("preInterviewReminderClaimedAt")
                .set("updatedAt", sentAt);
        mongoTemplate.updateFirst(query, update, Session.class);
    }

    private void releaseClaim(String sessionId) {
        Query query = Query.query(Criteria.where("id").is(sessionId));
        Update update = new Update().unset("preInterviewReminderClaimedAt");
        mongoTemplate.updateFirst(query, update, Session.class);
    }

    private Optional<Integer> dueOffset(Session session, Instant now) {
        Optional<OffsetDateTime> scheduled = schedulingTimeService.tryParseStartTime(session.getStartTime());
        if (scheduled.isEmpty()) {
            log.warn("Skipping reminder for session {} because startTime is invalid.", session.getId());
            return Optional.empty();
        }
        Instant start = scheduled.get().toInstant();
        if (!start.isAfter(now)) return Optional.empty();
        return reminderOffsetsMinutes
                .stream()
                .filter(offset -> start.minus(Duration.ofMinutes(offset)).compareTo(now) <= 0)
                .filter(offset -> isReminderOpenForOffset(session, session.getInterviewerId(), session.getCandidateId(), offset))
                .min(Comparator.naturalOrder());
    }

    private boolean isRecipientReminderOpen(Session session, String userId, int minutesBeforeStart) {
        if (minutesBeforeStart == 30) {
            if (userId != null && userId.equals(session.getCandidateId()) && session.getIntervieweeReminderSentAt() != null) {
                return false;
            }
            if (userId != null && userId.equals(session.getInterviewerId()) && session.getInterviewerReminderSentAt() != null) {
                return false;
            }
        }
        List<String> keys = session.getReminderDispatchKeys();
        return keys.stream().noneMatch(key -> key.startsWith(userId + ":") && key.endsWith(":" + minutesBeforeStart));
    }

    private boolean isReminderOpenForOffset(Session session, String interviewerId, String intervieweeId, int minutesBeforeStart) {
        return isRecipientReminderOpen(session, interviewerId, minutesBeforeStart)
                || isRecipientReminderOpen(session, intervieweeId, minutesBeforeStart);
    }

    private void markDispatchKeySent(String sessionId, String key, Instant sentAt, String error) {
        Query query = Query.query(Criteria.where("id").is(sessionId));
        Update update = new Update()
                .addToSet("reminderDispatchKeys", key)
                .unset("preInterviewReminderClaimedAt")
                .set("updatedAt", sentAt);
        mongoTemplate.updateFirst(query, update, Session.class);
    }

    private void addDispatchKeyLocal(Session session, String key) {
        List<String> keys = new ArrayList<>(session.getReminderDispatchKeys());
        if (!keys.contains(key)) {
            keys.add(key);
            session.setReminderDispatchKeys(keys);
        }
    }

    private void markReminderFailure(String sessionId, Instant failedAt, String message) {
        Query query = Query.query(Criteria.where("id").is(sessionId));
        Update update = new Update()
                .set("lastReminderFailureAt", failedAt)
                .set("lastReminderFailureMessage", message)
                .set("updatedAt", failedAt);
        mongoTemplate.updateFirst(query, update, Session.class);
    }

    private String dispatchKey(String userId, String channel, int minutesBeforeStart) {
        return userId + ":" + channel + ":" + minutesBeforeStart;
    }

    private List<Integer> normalizeOffsets(int legacyMinutesBefore) {
        List<Integer> offsets = new ArrayList<>(DEFAULT_OFFSETS);
        if (!offsets.contains(legacyMinutesBefore) && legacyMinutesBefore > 0) {
            offsets.add(legacyMinutesBefore);
        }
        offsets.sort(Comparator.reverseOrder());
        return offsets;
    }

    private String leadTimeLabel(int minutes) {
        if (minutes >= 1440) return "24 hours";
        if (minutes == 60) return "1 hour";
        return minutes + " minutes";
    }

    private String calendarEventUrl(Session session) {
        return frontendUrl + "/pages/dashboard.html#/sessions" + (session.getId() == null ? "" : "?sessionId=" + session.getId());
    }

    private String interviewerMeetingLink(Session session) {
        return session.getHostUrl() == null || session.getHostUrl().isBlank()
                ? session.getJoinUrl()
                : session.getHostUrl();
    }

    private String requireEmail(User user, String role, String sessionId) {
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new IllegalStateException("Missing " + role + " email for session " + sessionId);
        }
        return user.getEmail();
    }

    private ZonedDateTime recipientSchedule(OffsetDateTime scheduled, User user) {
        String timeZone = user == null ? null : user.getTimeZone();
        if (timeZone == null || timeZone.isBlank()) {
            return scheduled.toZonedDateTime();
        }
        try {
            return scheduled.atZoneSameInstant(ZoneId.of(timeZone.trim()));
        } catch (RuntimeException ignored) {
            return scheduled.toZonedDateTime();
        }
    }

    private String timezoneLabel(ZonedDateTime scheduled) {
        if (scheduled.getOffset().equals(ZoneOffset.UTC)) {
            return "UTC";
        }
        return scheduled.getZone().getId();
    }

    private String toString(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    public record ReminderDiagnostics(
            boolean enabled,
            List<Integer> reminderOffsetsMinutes,
            int batchSize,
            long staleClaimMinutes,
            String lastRunAt,
            String lastSuccessAt,
            String lastFailureAt,
            String lastFailureMessage
    ) {
    }
}
