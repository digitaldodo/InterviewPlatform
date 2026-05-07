package com.interview.platform.service;

import com.interview.platform.model.Feedback;
import com.interview.platform.model.Session;
import com.interview.platform.model.User;
import com.interview.platform.repository.FeedbackRepository;
import com.interview.platform.repository.SessionRepository;
import com.interview.platform.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final SessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final InterviewReportService interviewReportService;

    public FeedbackService(FeedbackRepository feedbackRepository, SessionRepository sessionRepository,
                           UserRepository userRepository, NotificationService notificationService,
                           InterviewReportService interviewReportService) {
        this.feedbackRepository = feedbackRepository;
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.interviewReportService = interviewReportService;
    }

    public Feedback submitFeedback(Feedback feedback) {
        if (feedback == null) {
            throw new IllegalArgumentException("Feedback details are required");
        }
        if (feedback.getRating() < 1 || feedback.getRating() > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5");
        }
        if (feedback.getReviewerId() == null || feedback.getReviewerId().isBlank()) {
            throw new IllegalArgumentException("Reviewer ID is required");
        }
        if (feedback.getComments() == null || feedback.getComments().isBlank()) {
            throw new IllegalArgumentException("Comments are required");
        }
        if (feedback.getSessionId() == null || !sessionRepository.existsById(feedback.getSessionId())) {
            throw new IllegalArgumentException("Invalid or non-existent session ID");
        }
        feedback.setComments(feedback.getComments().trim());
        normalizeStructuredFeedback(feedback);
        feedback.setCreatedAt(Instant.now());
        Feedback saved = feedbackRepository.save(feedback);
        updateInterviewerRating(feedback.getSessionId());
        try {
            interviewReportService.upsertFromFeedback(saved);
        } catch (RuntimeException ignored) {
            // Feedback must remain durable even if report generation fails.
        }
        sessionRepository.findById(feedback.getSessionId()).ifPresent(session ->
                notificationService.create(session.getCandidateId(), "FEEDBACK_SUBMITTED", "Feedback received",
                        "New feedback is available for " + session.getTitle() + ".",
                        java.util.Map.of("sessionId", session.getId())));
        return saved;
    }

    public List<Feedback> getAllFeedback() {
        return feedbackRepository.findAll();
    }

    public List<Feedback> getFeedbackForSession(String sessionId) {
        return feedbackRepository.findBySessionId(sessionId);
    }

    private void updateInterviewerRating(String sessionId) {
        Session session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null || session.getInterviewerId() == null) return;
        List<Session> sessions = sessionRepository.findByInterviewerId(session.getInterviewerId());
        List<String> ids = sessions.stream().map(Session::getId).toList();
        List<Feedback> all = feedbackRepository.findAll().stream()
                .filter(item -> ids.contains(item.getSessionId()))
                .toList();
        if (all.isEmpty()) return;
        double avg = all.stream().mapToInt(Feedback::getRating).average().orElse(0.0);
        User interviewer = userRepository.findById(session.getInterviewerId()).orElse(null);
        if (interviewer == null) return;
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
            topic.setTopic(topic.getTopic().trim());
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
    }
}
