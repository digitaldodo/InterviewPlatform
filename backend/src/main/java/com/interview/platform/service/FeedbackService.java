package com.interview.platform.service;

import com.interview.platform.dto.FeedbackDtos;
import com.interview.platform.exception.ResourceNotFoundException;
import com.interview.platform.exception.UnauthorizedException;
import com.interview.platform.model.Feedback;
import com.interview.platform.model.Session;
import com.interview.platform.model.User;
import com.interview.platform.repository.FeedbackRepository;
import com.interview.platform.repository.SessionRepository;
import com.interview.platform.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final SessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final InterviewReportService interviewReportService;
    private final ReviewIntegrityService reviewIntegrityService;
    private final CacheInvalidationService cacheInvalidationService;

    public FeedbackService(FeedbackRepository feedbackRepository,
                           SessionRepository sessionRepository,
                           UserRepository userRepository,
                           NotificationService notificationService,
                           InterviewReportService interviewReportService,
                           ReviewIntegrityService reviewIntegrityService,
                           CacheInvalidationService cacheInvalidationService) {
        this.feedbackRepository = feedbackRepository;
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.interviewReportService = interviewReportService;
        this.reviewIntegrityService = reviewIntegrityService;
        this.cacheInvalidationService = cacheInvalidationService;
    }

    public FeedbackDtos.FeedbackItem submitFeedback(User actor, Feedback feedback) {
        if (feedback == null) {
            throw new IllegalArgumentException("Feedback details are required");
        }
        if (feedback.getRating() < 1 || feedback.getRating() > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5");
        }
        if (actor == null || actor.getId() == null || actor.getId().isBlank()) {
            throw new IllegalArgumentException("Authenticated reviewer is required");
        }
        if (feedback.getComments() == null || feedback.getComments().isBlank()) {
            throw new IllegalArgumentException("Comments are required");
        }
        Session session = sessionRepository.findById(feedback.getSessionId())
                .orElseThrow(() -> new IllegalArgumentException("Invalid or non-existent session ID"));
        boolean candidateReviewer = actor.getId().equals(session.getCandidateId());
        boolean interviewerReviewer = actor.getId().equals(session.getInterviewerId());
        if (!candidateReviewer && !interviewerReviewer) {
            throw new IllegalArgumentException("Only session participants can submit feedback");
        }
        if (!"COMPLETED".equalsIgnoreCase(session.getStatus())) {
            throw new IllegalArgumentException("Feedback can only be submitted after the session is completed");
        }
        if (feedbackRepository.existsBySessionIdAndReviewerId(session.getId(), actor.getId())) {
            throw new IllegalArgumentException("You have already submitted feedback for this session");
        }

        normalizeStructuredFeedback(feedback);
        ReviewIntegrityService.ReviewIntegrityResult integrity = reviewIntegrityService.analyze(
                actor,
                session,
                feedback,
                feedbackRepository.findTop10ByReviewerIdOrderByCreatedAtDesc(actor.getId())
        );

        feedback.setComments(integrity.normalizedComments());
        feedback.setReviewerId(actor.getId());
        feedback.setInterviewerId(session.getInterviewerId());
        feedback.setTargetUserId(candidateReviewer ? session.getInterviewerId() : session.getCandidateId());
        feedback.setReviewType(candidateReviewer ? "INTERVIEWER_REVIEW" : "SESSION_FEEDBACK");
        feedback.setPublicReview(candidateReviewer && !integrity.flaggedForModeration());
        feedback.setFlaggedForModeration(integrity.flaggedForModeration());
        feedback.setSuspiciousFlags(integrity.suspiciousFlags());
        feedback.setSuspiciousScore(integrity.suspiciousScore());
        feedback.setReviewQualityScore(integrity.reviewQualityScore());
        feedback.setContentFingerprint(integrity.contentFingerprint());
        feedback.setReviewedWindowStartedAt(integrity.rateLimitWindowStart());
        feedback.setCreatedAt(Instant.now());

        Feedback saved = feedbackRepository.save(feedback);
        updateInterviewerRating(feedback.getSessionId());
        try {
            interviewReportService.upsertFromFeedback(saved);
        } catch (RuntimeException ignored) {
            // Feedback must remain durable even if report generation fails.
        }
        notificationService.create(
                session.getCandidateId(),
                "FEEDBACK_SUBMITTED",
                "Feedback received",
                integrity.flaggedForModeration()
                        ? "New feedback is awaiting moderation review for " + session.getTitle() + "."
                        : "New feedback is available for " + session.getTitle() + ".",
                java.util.Map.of("sessionId", session.getId())
        );
        notificationService.create(
                session.getInterviewerId(),
                "FEEDBACK_SUBMITTED",
                "Feedback received",
                integrity.flaggedForModeration()
                        ? "New feedback is awaiting moderation review for " + session.getTitle() + "."
                        : "New feedback is available for " + session.getTitle() + ".",
                java.util.Map.of("sessionId", session.getId())
        );
        cacheInvalidationService.evictAnalyticsForParticipants(session.getCandidateId(), session.getInterviewerId());
        cacheInvalidationService.evictInterviewerCaches(session.getInterviewerId(),
                userRepository.findById(session.getInterviewerId()).map(User::getUsername).orElse(null));
        return toFeedbackItem(saved);
    }

    public List<FeedbackDtos.FeedbackItem> getFeedbackForUser(User actor) {
        requireActor(actor);
        if (isAdmin(actor)) {
            return feedbackRepository.findAll().stream()
                    .sorted(Comparator.comparing(Feedback::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                    .map(this::toFeedbackItem)
                    .toList();
        }
        List<String> sessionIds = sessionRepository.findByInterviewerIdOrCandidateId(actor.getId(), actor.getId()).stream()
                .map(Session::getId)
                .filter(id -> id != null && !id.isBlank())
                .toList();
        if (sessionIds.isEmpty()) {
            return List.of();
        }
        return feedbackRepository.findBySessionIdIn(sessionIds).stream()
                .sorted(Comparator.comparing(Feedback::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toFeedbackItem)
                .toList();
    }

    public List<FeedbackDtos.FeedbackItem> getFeedbackForSession(String sessionId, User actor) {
        requireActor(actor);
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));
        if (!isAdmin(actor)
                && !actor.getId().equals(session.getCandidateId())
                && !actor.getId().equals(session.getInterviewerId())) {
            throw new UnauthorizedException("You do not have access to this session feedback");
        }
        return feedbackRepository.findBySessionId(sessionId).stream()
                .sorted(Comparator.comparing(Feedback::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toFeedbackItem)
                .toList();
    }

    public List<FeedbackDtos.PublicFeedbackItem> publicReviewsForInterviewer(String interviewerId) {
        return feedbackRepository.findByInterviewerIdAndPublicReviewTrueOrderByCreatedAtDesc(interviewerId).stream()
                .map(this::toPublicFeedbackItem)
                .toList();
    }

    private void updateInterviewerRating(String sessionId) {
        Session session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null || session.getInterviewerId() == null) return;
        List<Session> sessions = sessionRepository.findByInterviewerId(session.getInterviewerId());
        List<Feedback> all = feedbackRepository.findByInterviewerIdAndPublicReviewTrue(session.getInterviewerId());
        User interviewer = userRepository.findById(session.getInterviewerId()).orElse(null);
        if (interviewer == null) return;
        double avg = all.stream().mapToInt(Feedback::getRating).average().orElse(0.0);
        interviewer.setAverageRating(Math.round(avg * 10.0) / 10.0);
        interviewer.setReviewCount(all.size());
        interviewer.setCompletedInterviews((int) sessions.stream()
                .filter(s -> "COMPLETED".equalsIgnoreCase(s.getStatus()))
                .count());
        userRepository.save(interviewer);
    }

    private void normalizeStructuredFeedback(Feedback feedback) {
        if ((feedback.getImprovementAreas() == null || feedback.getImprovementAreas().isBlank())
                && feedback.getRecommendations() != null && !feedback.getRecommendations().isBlank()) {
            feedback.setImprovementAreas(feedback.getRecommendations().trim());
        }
        if ((feedback.getRecommendations() == null || feedback.getRecommendations().isBlank())
                && feedback.getImprovementAreas() != null && !feedback.getImprovementAreas().isBlank()) {
            feedback.setRecommendations(feedback.getImprovementAreas().trim());
        }
        feedback.getTopicFeedback().forEach(topic -> {
            if (topic.getTopic() == null || topic.getTopic().isBlank()) {
                throw new IllegalArgumentException("Topic feedback requires a topic");
            }
            topic.setTopic(topic.getTopic().trim().replaceAll("\\s+", " "));
            Integer rating = topic.getRating();
            if (rating != null && rating != 0 && (rating < 1 || rating > 5)) {
                throw new IllegalArgumentException("Topic ratings must be between 1 and 5");
            }
            topic.getSkillRatings().forEach((skill, value) -> {
                if (skill == null || skill.isBlank()) {
                    throw new IllegalArgumentException("Skill feedback requires a skill name");
                }
                if (value != null && value != 0 && (value < 1 || value > 5)) {
                    throw new IllegalArgumentException("Skill ratings must be between 1 and 5");
                }
            });
        });
        if (feedback.getReviewType() != null) {
            feedback.setReviewType(feedback.getReviewType().trim().toUpperCase(Locale.ROOT));
        }
    }

    private FeedbackDtos.FeedbackItem toFeedbackItem(Feedback feedback) {
        return new FeedbackDtos.FeedbackItem(
                feedback.getId(),
                feedback.getSessionId(),
                feedback.getReviewerId(),
                feedback.getInterviewerId(),
                feedback.getRating(),
                feedback.getComments(),
                feedback.getStrengths(),
                feedback.getWeaknesses(),
                feedback.getCommunication(),
                feedback.getTechnicalSkills(),
                feedback.getRecommendations(),
                feedback.getImprovementAreas(),
                feedback.getReviewType(),
                feedback.getPublicReview(),
                feedback.getFlaggedForModeration(),
                feedback.getSuspiciousFlags(),
                feedback.getSuspiciousScore(),
                feedback.getReviewQualityScore(),
                feedback.getCreatedAt() == null ? null : feedback.getCreatedAt().toString(),
                feedback.getTopicFeedback().stream()
                        .map(this::toTopicSummary)
                        .toList()
        );
    }

    private FeedbackDtos.PublicFeedbackItem toPublicFeedbackItem(Feedback feedback) {
        return new FeedbackDtos.PublicFeedbackItem(
                feedback.getId(),
                feedback.getRating(),
                feedback.getComments(),
                feedback.getReviewQualityScore(),
                feedback.getCreatedAt() == null ? null : feedback.getCreatedAt().toString(),
                feedback.getTopicFeedback().stream()
                        .map(this::toTopicSummary)
                        .toList()
        );
    }

    private FeedbackDtos.TopicFeedbackSummary toTopicSummary(Feedback.TopicFeedback topic) {
        return new FeedbackDtos.TopicFeedbackSummary(
                topic.getTopic(),
                topic.getRating(),
                topic.getSkillRatings(),
                topic.getStrengths(),
                topic.getImprovementAreas(),
                topic.getComments()
        );
    }

    private void requireActor(User actor) {
        if (actor == null || actor.getId() == null || actor.getId().isBlank()) {
            throw new UnauthorizedException("Authentication required");
        }
    }

    private boolean isAdmin(User actor) {
        return actor != null && actor.hasRole("ADMIN");
    }
}
