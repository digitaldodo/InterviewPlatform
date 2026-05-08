package com.interview.platform.service;

import com.interview.platform.dto.FeedbackDtos;
import com.interview.platform.exception.ResourceNotFoundException;
import com.interview.platform.exception.UnauthorizedException;
import com.interview.platform.model.Feedback;
import com.interview.platform.model.FeedbackDraft;
import com.interview.platform.model.Session;
import com.interview.platform.model.User;
import com.interview.platform.repository.FeedbackDraftRepository;
import com.interview.platform.repository.FeedbackRepository;
import com.interview.platform.repository.SessionRepository;
import com.interview.platform.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final FeedbackDraftRepository feedbackDraftRepository;
    private final SessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final InterviewReportService interviewReportService;
    private final ReviewIntegrityService reviewIntegrityService;
    private final CacheInvalidationService cacheInvalidationService;

    public FeedbackService(FeedbackRepository feedbackRepository,
                           FeedbackDraftRepository feedbackDraftRepository,
                           SessionRepository sessionRepository,
                           UserRepository userRepository,
                           NotificationService notificationService,
                           InterviewReportService interviewReportService,
                           ReviewIntegrityService reviewIntegrityService,
                           CacheInvalidationService cacheInvalidationService) {
        this.feedbackRepository = feedbackRepository;
        this.feedbackDraftRepository = feedbackDraftRepository;
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
        if (interviewerReviewer) {
            feedback.setShareWithInterviewee(feedback.getShareWithInterviewee());
        }
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
        if (!interviewerReviewer || feedback.getShareWithInterviewee()) {
            notificationService.create(
                    session.getCandidateId(),
                    "FEEDBACK_SUBMITTED",
                    "Feedback received",
                    integrity.flaggedForModeration()
                            ? "New feedback is awaiting moderation review for " + session.getTitle() + "."
                            : "New feedback is available for " + session.getTitle() + ".",
                    java.util.Map.of("sessionId", session.getId())
            );
        }
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
        return toFeedbackItem(saved, actor, session);
    }

    public List<FeedbackDtos.FeedbackItem> getFeedbackForUser(User actor) {
        requireActor(actor);
        if (isAdmin(actor)) {
            return feedbackRepository.findAll().stream()
                    .sorted(Comparator.comparing(Feedback::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                    .map(feedback -> toFeedbackItem(feedback, actor, null))
                    .toList();
        }
        List<Session> actorSessions = sessionRepository.findByInterviewerIdOrCandidateId(actor.getId(), actor.getId());
        Map<String, Session> sessionById = actorSessions.stream()
                .filter(session -> session.getId() != null && !session.getId().isBlank())
                .collect(Collectors.toMap(Session::getId, Function.identity(), (left, right) -> left));
        List<String> sessionIds = actorSessions.stream()
                .map(Session::getId)
                .filter(id -> id != null && !id.isBlank())
                .toList();
        if (sessionIds.isEmpty()) {
            return List.of();
        }
        return feedbackRepository.findBySessionIdIn(sessionIds).stream()
                .sorted(Comparator.comparing(Feedback::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(feedback -> toFeedbackItem(feedback, actor, sessionById.get(feedback.getSessionId())))
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
                .map(feedback -> toFeedbackItem(feedback, actor, session))
                .toList();
    }

    public FeedbackDtos.FeedbackDraftItem getEvaluationDraft(String sessionId, User actor) {
        Session session = requireAssignedInterviewer(sessionId, actor);
        return feedbackDraftRepository.findBySessionIdAndInterviewerId(session.getId(), actor.getId())
                .map(draft -> toDraftItem(draft, feedbackRepository.existsBySessionIdAndReviewerId(session.getId(), actor.getId())))
                .orElseGet(() -> emptyDraft(session, actor));
    }

    public FeedbackDtos.FeedbackDraftItem saveEvaluationDraft(String sessionId, User actor, FeedbackDtos.FeedbackDraftRequest request) {
        Session session = requireAssignedInterviewer(sessionId, actor);
        if (feedbackRepository.existsBySessionIdAndReviewerId(session.getId(), actor.getId())) {
            throw new IllegalArgumentException("Final feedback has already been submitted for this session");
        }
        FeedbackDraft draft = feedbackDraftRepository.findBySessionIdAndInterviewerId(session.getId(), actor.getId())
                .orElseGet(() -> {
                    FeedbackDraft created = new FeedbackDraft();
                    created.setSessionId(session.getId());
                    created.setInterviewerId(actor.getId());
                    created.setCreatedAt(Instant.now());
                    return created;
                });
        applyDraftRequest(draft, request);
        draft.setUpdatedAt(Instant.now());
        return toDraftItem(feedbackDraftRepository.save(draft), false);
    }

    public FeedbackDtos.FeedbackItem submitEvaluationDraft(String sessionId, User actor, FeedbackDtos.FeedbackDraftRequest request) {
        Session session = requireAssignedInterviewer(sessionId, actor);
        if (!"COMPLETED".equalsIgnoreCase(session.getStatus())) {
            throw new IllegalArgumentException("Finalize feedback after marking the session completed");
        }
        FeedbackDraft draft = feedbackDraftRepository.findBySessionIdAndInterviewerId(session.getId(), actor.getId())
                .orElseGet(() -> {
                    FeedbackDraft created = new FeedbackDraft();
                    created.setSessionId(session.getId());
                    created.setInterviewerId(actor.getId());
                    created.setCreatedAt(Instant.now());
                    return created;
                });
        applyDraftRequest(draft, request);
        Feedback feedback = feedbackFromDraft(draft);
        FeedbackDtos.FeedbackItem submitted = submitFeedback(actor, feedback);
        feedbackDraftRepository.deleteBySessionId(session.getId());
        return submitted;
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
        feedback.setRatingLevel(trimToNull(feedback.getRatingLevel()));
        feedback.setHiringRecommendation(trimToNull(feedback.getHiringRecommendation()));
        feedback.setCommunicationNotes(trimToNull(feedback.getCommunicationNotes()));
        feedback.setCodingQualityNotes(trimToNull(feedback.getCodingQualityNotes()));
        feedback.setProblemSolvingNotes(trimToNull(feedback.getProblemSolvingNotes()));
        feedback.setFinalSummary(trimToNull(feedback.getFinalSummary()));
        feedback.setPrivateNotes(trimToNull(feedback.getPrivateNotes()));
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

    private FeedbackDtos.FeedbackItem toFeedbackItem(Feedback feedback, User viewer, Session session) {
        boolean reviewerView = viewer != null && feedback.getReviewerId() != null && feedback.getReviewerId().equals(viewer.getId());
        boolean adminView = isAdmin(viewer);
        boolean interviewerAuthored = session != null && feedback.getReviewerId() != null && feedback.getReviewerId().equals(session.getInterviewerId());
        boolean redactForInterviewee = interviewerAuthored
                && viewer != null
                && viewer.getId() != null
                && viewer.getId().equals(session.getCandidateId())
                && !feedback.getShareWithInterviewee();
        String redactedNote = "Private interviewer evaluation submitted.";
        return new FeedbackDtos.FeedbackItem(
                feedback.getId(),
                feedback.getSessionId(),
                feedback.getReviewerId(),
                feedback.getInterviewerId(),
                feedback.getRating(),
                redactForInterviewee ? redactedNote : feedback.getComments(),
                redactForInterviewee ? null : feedback.getStrengths(),
                redactForInterviewee ? null : feedback.getWeaknesses(),
                redactForInterviewee ? 0 : feedback.getCommunication(),
                redactForInterviewee ? 0 : feedback.getTechnicalSkills(),
                redactForInterviewee ? null : feedback.getRatingLevel(),
                redactForInterviewee ? null : feedback.getHiringRecommendation(),
                redactForInterviewee ? null : feedback.getCommunicationNotes(),
                redactForInterviewee ? null : feedback.getCodingQualityNotes(),
                redactForInterviewee ? null : feedback.getProblemSolvingNotes(),
                redactForInterviewee ? null : feedback.getFinalSummary(),
                (reviewerView || adminView) ? feedback.getPrivateNotes() : null,
                feedback.getShareWithInterviewee(),
                redactForInterviewee ? null : feedback.getRecommendations(),
                redactForInterviewee ? null : feedback.getImprovementAreas(),
                feedback.getReviewType(),
                feedback.getPublicReview(),
                feedback.getFlaggedForModeration(),
                feedback.getSuspiciousFlags(),
                feedback.getSuspiciousScore(),
                feedback.getReviewQualityScore(),
                feedback.getCreatedAt() == null ? null : feedback.getCreatedAt().toString(),
                redactForInterviewee ? List.of() : feedback.getTopicFeedback().stream()
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
                topic.getExamples(),
                topic.getStrengths(),
                topic.getWeaknesses(),
                topic.getImprovementAreas(),
                topic.getComments()
        );
    }

    private FeedbackDtos.FeedbackDraftItem emptyDraft(Session session, User actor) {
        return new FeedbackDtos.FeedbackDraftItem(
                null,
                session.getId(),
                actor.getId(),
                0,
                0,
                0,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                true,
                null,
                null,
                List.of(),
                feedbackRepository.existsBySessionIdAndReviewerId(session.getId(), actor.getId())
        );
    }

    private FeedbackDtos.FeedbackDraftItem toDraftItem(FeedbackDraft draft, boolean submitted) {
        return new FeedbackDtos.FeedbackDraftItem(
                draft.getId(),
                draft.getSessionId(),
                draft.getInterviewerId(),
                draft.getRating(),
                draft.getCommunication(),
                draft.getTechnicalSkills(),
                draft.getRatingLevel(),
                draft.getStrengths(),
                draft.getWeaknesses(),
                draft.getHiringRecommendation(),
                draft.getCommunicationNotes(),
                draft.getCodingQualityNotes(),
                draft.getProblemSolvingNotes(),
                draft.getFinalSummary(),
                draft.getShareableFeedback(),
                draft.getPrivateNotes(),
                draft.getShareWithInterviewee(),
                draft.getCreatedAt() == null ? null : draft.getCreatedAt().toString(),
                draft.getUpdatedAt() == null ? null : draft.getUpdatedAt().toString(),
                draft.getTopicFeedback().stream().map(this::toTopicSummary).toList(),
                submitted
        );
    }

    private void applyDraftRequest(FeedbackDraft draft, FeedbackDtos.FeedbackDraftRequest request) {
        if (request == null) {
            return;
        }
        draft.setRating(clampRating(request.rating()));
        draft.setCommunication(clampRating(request.communication()));
        draft.setTechnicalSkills(clampRating(request.technicalSkills()));
        draft.setRatingLevel(trimToNull(request.ratingLevel()));
        draft.setStrengths(trimToNull(request.strengths()));
        draft.setWeaknesses(trimToNull(request.weaknesses()));
        draft.setHiringRecommendation(trimToNull(request.hiringRecommendation()));
        draft.setCommunicationNotes(trimToNull(request.communicationNotes()));
        draft.setCodingQualityNotes(trimToNull(request.codingQualityNotes()));
        draft.setProblemSolvingNotes(trimToNull(request.problemSolvingNotes()));
        draft.setFinalSummary(trimToNull(request.finalSummary()));
        draft.setShareableFeedback(trimToNull(request.shareableFeedback()));
        draft.setPrivateNotes(trimToNull(request.privateNotes()));
        draft.setShareWithInterviewee(request.shareWithInterviewee());
        draft.setTopicFeedback((request.topicFeedback() == null ? List.<FeedbackDtos.TopicFeedbackSummary>of() : request.topicFeedback()).stream()
                .map(this::topicFromSummary)
                .toList());
    }

    private Feedback feedbackFromDraft(FeedbackDraft draft) {
        Feedback feedback = new Feedback();
        feedback.setSessionId(draft.getSessionId());
        feedback.setRating(draft.getRating() > 0 ? draft.getRating() : 3);
        feedback.setCommunication(draft.getCommunication());
        feedback.setTechnicalSkills(draft.getTechnicalSkills());
        feedback.setRatingLevel(draft.getRatingLevel());
        feedback.setStrengths(draft.getStrengths());
        feedback.setWeaknesses(draft.getWeaknesses());
        feedback.setHiringRecommendation(draft.getHiringRecommendation());
        feedback.setCommunicationNotes(draft.getCommunicationNotes());
        feedback.setCodingQualityNotes(draft.getCodingQualityNotes());
        feedback.setProblemSolvingNotes(draft.getProblemSolvingNotes());
        feedback.setFinalSummary(draft.getFinalSummary());
        feedback.setPrivateNotes(draft.getPrivateNotes());
        feedback.setShareWithInterviewee(draft.getShareWithInterviewee());
        String comments = firstText(draft.getShareableFeedback(), draft.getFinalSummary(), draft.getProblemSolvingNotes(),
                "Private interviewer evaluation recorded for this session.");
        feedback.setComments(comments.length() < 12 ? comments + " evaluation" : comments);
        String improvements = firstText(draft.getWeaknesses(), draft.getCodingQualityNotes(), draft.getCommunicationNotes());
        feedback.setImprovementAreas(improvements);
        feedback.setRecommendations(firstText(draft.getHiringRecommendation(), improvements));
        feedback.setTopicFeedback(draft.getTopicFeedback());
        return feedback;
    }

    private Feedback.TopicFeedback topicFromSummary(FeedbackDtos.TopicFeedbackSummary summary) {
        Feedback.TopicFeedback topic = new Feedback.TopicFeedback();
        topic.setTopic(trimToNull(summary.topic()));
        topic.setRating(clampRating(summary.rating()));
        topic.setSkillRatings(summary.skillRatings() == null ? Map.of() : summary.skillRatings());
        topic.setExamples(trimToNull(summary.examples()));
        topic.setStrengths(trimToNull(summary.strengths()));
        topic.setWeaknesses(trimToNull(summary.weaknesses()));
        topic.setImprovementAreas(trimToNull(summary.improvementAreas()));
        topic.setComments(trimToNull(summary.comments()));
        return topic;
    }

    private Session requireAssignedInterviewer(String sessionId, User actor) {
        requireActor(actor);
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));
        if (!actor.getId().equals(session.getInterviewerId())) {
            throw new UnauthorizedException("Only the assigned interviewer can edit this evaluation");
        }
        return session;
    }

    private Integer clampRating(Integer value) {
        if (value == null) return 0;
        if (value < 0 || value > 5) {
            throw new IllegalArgumentException("Ratings must be between 1 and 5");
        }
        return value;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private String firstText(String... values) {
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null) return trimmed;
        }
        return null;
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
