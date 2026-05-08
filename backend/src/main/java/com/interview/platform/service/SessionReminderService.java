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
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

@Service
public class SessionReminderService {
    private static final Logger log = LoggerFactory.getLogger(SessionReminderService.class);

    private static final String CONFIRMED = "CONFIRMED";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    private final SessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final NotificationService notificationService;
    private final SchedulingTimeService schedulingTimeService;
    private final MongoTemplate mongoTemplate;
    private final boolean enabled;
    private final int minutesBefore;
    private final int batchSize;
    private final Duration staleClaimAfter;

    public SessionReminderService(SessionRepository sessionRepository,
                                  UserRepository userRepository,
                                  EmailService emailService,
                                  NotificationService notificationService,
                                  SchedulingTimeService schedulingTimeService,
                                  MongoTemplate mongoTemplate,
                                  @Value("${app.reminders.pre-interview.enabled:true}") boolean enabled,
                                  @Value("${app.reminders.pre-interview.minutes-before:30}") int minutesBefore,
                                  @Value("${app.reminders.pre-interview.batch-size:100}") int batchSize,
                                  @Value("${app.reminders.pre-interview.stale-claim-minutes:5}") int staleClaimMinutes) {
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.notificationService = notificationService;
        this.schedulingTimeService = schedulingTimeService;
        this.mongoTemplate = mongoTemplate;
        this.enabled = enabled;
        this.minutesBefore = Math.max(1, minutesBefore);
        this.batchSize = Math.max(1, batchSize);
        this.staleClaimAfter = Duration.ofMinutes(Math.max(1, staleClaimMinutes));
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
        try {
            sessionRepository.findByStatusAndPreInterviewReminderSentAtIsNull(
                            CONFIRMED,
                            PageRequest.of(0, batchSize, Sort.by(Sort.Direction.ASC, "startTime"))
                    ).stream()
                    .filter(session -> isReminderDue(session, now))
                    .forEach(session -> sendReminderSafely(session, now));
        } catch (RuntimeException ex) {
            log.error("Pre-interview reminder scheduler failed safely; next run will retry.", ex);
        }
    }

    private boolean isReminderDue(Session session, Instant now) {
        Optional<OffsetDateTime> scheduled = schedulingTimeService.tryParseStartTime(session.getStartTime());
        if (scheduled.isEmpty()) {
            log.warn("Skipping reminder for session {} because startTime is invalid.", session.getId());
            return false;
        }
        Instant start = scheduled.get().toInstant();
        Instant dueAt = start.minus(Duration.ofMinutes(minutesBefore));
        return start.isAfter(now) && !dueAt.isAfter(now);
    }

    private void sendReminderSafely(Session session, Instant now) {
        Optional<Session> claimed = claimSession(session.getId(), now);
        if (claimed.isEmpty()) {
            return;
        }
        try {
            sendReminder(claimed.get(), now);
        } catch (RuntimeException ex) {
            releaseClaim(claimed.get().getId());
            log.error("Pre-interview reminder failed for session {}; it will be retried safely. reason={}",
                    claimed.get().getId(), ex.getMessage());
        }
    }

    private void sendReminder(Session session, Instant now) {
        OffsetDateTime scheduled = schedulingTimeService.parseStartTime(session.getStartTime());
        User interviewer = userRepository.findById(session.getInterviewerId())
                .orElseThrow(() -> new IllegalStateException("Interviewer not found for session " + session.getId()));
        User interviewee = userRepository.findById(session.getCandidateId())
                .orElseThrow(() -> new IllegalStateException("Interviewee not found for session " + session.getId()));

        String date = scheduled.format(DATE_FORMATTER);
        String time = scheduled.format(TIME_FORMATTER);
        String timezone = timezoneLabel(scheduled);

        if (session.getIntervieweeReminderSentAt() == null) {
            emailService.sendPreInterviewReminder(
                    requireEmail(interviewee, "interviewee", session.getId()),
                    interviewee.getName(),
                    interviewer.getName(),
                    interviewee.getName(),
                    session.getTopics(),
                    date,
                    time,
                    timezone,
                    session.getJoinUrl(),
                    session.getDurationMinutes()
            );
            notificationService.create(
                    session.getCandidateId(),
                    "SESSION_REMINDER",
                    "Upcoming interview",
                    "Your " + session.getTitle() + " session starts at " + time + " (" + timezone + ").",
                    java.util.Map.of("sessionId", session.getId(), "startTime", session.getStartTime(), "route", "sessions")
            );
            markRecipientReminderSent(session.getId(), "intervieweeReminderSentAt", now);
            session.setIntervieweeReminderSentAt(now);
        }

        if (session.getInterviewerReminderSentAt() == null) {
            emailService.sendPreInterviewReminder(
                    requireEmail(interviewer, "interviewer", session.getId()),
                    interviewer.getName(),
                    interviewer.getName(),
                    interviewee.getName(),
                    session.getTopics(),
                    date,
                    time,
                    timezone,
                    interviewerMeetingLink(session),
                    session.getDurationMinutes()
            );
            notificationService.create(
                    session.getInterviewerId(),
                    "SESSION_REMINDER",
                    "Upcoming interview",
                    "Your " + session.getTitle() + " session starts at " + time + " (" + timezone + ").",
                    java.util.Map.of("sessionId", session.getId(), "startTime", session.getStartTime(), "route", "sessions")
            );
            markRecipientReminderSent(session.getId(), "interviewerReminderSentAt", now);
            session.setInterviewerReminderSentAt(now);
        }

        if (session.getIntervieweeReminderSentAt() != null && session.getInterviewerReminderSentAt() != null) {
            markSessionReminderComplete(session.getId(), now);
            log.info("Pre-interview reminder sent for session {}", session.getId());
        } else {
            releaseClaim(session.getId());
        }
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
                Criteria.where("preInterviewReminderSentAt").is(null),
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
        Query query = Query.query(Criteria.where("id").is(sessionId).and("preInterviewReminderSentAt").is(null));
        Update update = new Update()
                .set("preInterviewReminderSentAt", sentAt)
                .unset("preInterviewReminderClaimedAt")
                .set("updatedAt", sentAt);
        mongoTemplate.updateFirst(query, update, Session.class);
    }

    private void releaseClaim(String sessionId) {
        Query query = Query.query(Criteria.where("id").is(sessionId).and("preInterviewReminderSentAt").is(null));
        Update update = new Update().unset("preInterviewReminderClaimedAt");
        mongoTemplate.updateFirst(query, update, Session.class);
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

    private String timezoneLabel(OffsetDateTime scheduled) {
        if (scheduled.getOffset().equals(ZoneOffset.UTC)) {
            return "UTC";
        }
        return scheduled.getOffset().getId();
    }
}
