package com.interview.platform.service;

import com.interview.platform.dto.AdminDtos;
import com.interview.platform.exception.ResourceNotFoundException;
import com.interview.platform.model.Feedback;
import com.interview.platform.model.Session;
import com.interview.platform.model.User;
import com.interview.platform.model.UserReport;
import com.interview.platform.repository.FeedbackRepository;
import com.interview.platform.repository.SessionRepository;
import com.interview.platform.repository.UserReportRepository;
import com.interview.platform.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AdminService {
    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final UserReportRepository userReportRepository;
    private final FeedbackRepository feedbackRepository;

    public AdminService(UserRepository userRepository,
                        SessionRepository sessionRepository,
                        UserReportRepository userReportRepository,
                        FeedbackRepository feedbackRepository) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.userReportRepository = userReportRepository;
        this.feedbackRepository = feedbackRepository;
    }

    public AdminDtos.OverviewResponse overview() {
        List<User> users = userRepository.findAll();
        List<Session> sessions = sessionRepository.findAll();
        List<Feedback> reviews = feedbackRepository.findAll().stream()
                .filter(item -> Boolean.TRUE.equals(item.getPublicReview()))
                .toList();

        long totalUsers = users.size();
        long totalInterviewers = users.stream().filter(user -> user.hasRole("INTERVIEWER")).count();
        long totalAdmins = users.stream().filter(user -> user.hasRole("ADMIN")).count();
        long enabledUsers = users.stream().filter(user -> !Boolean.FALSE.equals(user.getAccountEnabled())).count();
        long verifiedInterviewers = users.stream().filter(user -> Boolean.TRUE.equals(user.getInterviewerVerified())).count();
        long totalSessions = sessions.size();
        long completedSessions = sessions.stream().filter(session -> "COMPLETED".equalsIgnoreCase(session.getStatus())).count();
        long cancelledSessions = sessions.stream().filter(session -> "CANCELLED".equalsIgnoreCase(session.getStatus())).count();
        long pendingSessions = sessions.stream().filter(session -> "PENDING".equalsIgnoreCase(session.getStatus()) || "CONFIRMED".equalsIgnoreCase(session.getStatus())).count();
        long openReports = userReportRepository.countByStatus("OPEN");
        long visiblePublicReviews = feedbackRepository.findByReviewTypeOrderByCreatedAtDesc("INTERVIEWER_REVIEW").stream()
                .filter(item -> Boolean.TRUE.equals(item.getPublicReview()))
                .count();
        long hiddenPublicReviews = feedbackRepository.findByReviewTypeOrderByCreatedAtDesc("INTERVIEWER_REVIEW").stream()
                .filter(item -> !Boolean.TRUE.equals(item.getPublicReview()))
                .count();
        double averageRating = Math.round(reviews.stream().mapToInt(Feedback::getRating).average().orElse(0.0) * 10.0) / 10.0;
        double completionRate = totalSessions == 0 ? 0.0 : Math.round((completedSessions * 1000.0) / totalSessions) / 10.0;
        double cancellationRate = totalSessions == 0 ? 0.0 : Math.round((cancelledSessions * 1000.0) / totalSessions) / 10.0;

        Map<String, Long> topicCounts = new HashMap<>();
        sessions.forEach(session -> session.getTopics().forEach(topic -> {
            if (topic != null && !topic.isBlank()) {
                topicCounts.merge(topic.trim(), 1L, Long::sum);
            }
        }));
        List<AdminDtos.TopicCount> topTopics = topicCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(8)
                .map(entry -> new AdminDtos.TopicCount(entry.getKey(), entry.getValue()))
                .toList();

        return new AdminDtos.OverviewResponse(
                totalUsers,
                totalInterviewers,
                totalAdmins,
                enabledUsers,
                verifiedInterviewers,
                totalSessions,
                completedSessions,
                cancelledSessions,
                pendingSessions,
                openReports,
                visiblePublicReviews,
                hiddenPublicReviews,
                averageRating,
                completionRate,
                cancellationRate,
                topTopics
        );
    }

    public List<User> users(String q, String role, Boolean enabled) {
        String query = normalize(q);
        String normalizedRole = normalizeRole(role);
        return userRepository.findAll().stream()
                .filter(user -> query == null || matchesUserQuery(user, query))
                .filter(user -> normalizedRole == null || user.hasRole(normalizedRole))
                .filter(user -> enabled == null || enabled.equals(!Boolean.FALSE.equals(user.getAccountEnabled())))
                .sorted(Comparator.comparing(User::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public List<User> interviewers(Boolean verified) {
        return userRepository.findByRole("INTERVIEWER").stream()
                .filter(user -> verified == null || verified.equals(Boolean.TRUE.equals(user.getInterviewerVerified())))
                .sorted(Comparator.comparing(User::getAverageRating, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public List<Session> sessions(String status) {
        String normalized = normalize(status);
        return sessionRepository.findAll().stream()
                .filter(session -> normalized == null || normalized.equalsIgnoreCase(session.getStatus()))
                .sorted(Comparator.comparing(this::sessionSortValue).reversed())
                .toList();
    }

    public List<UserReport> reports(String status) {
        String normalized = normalize(status);
        if (normalized != null) {
            return userReportRepository.findByStatusOrderByCreatedAtDesc(normalized.toUpperCase(Locale.ROOT));
        }
        return userReportRepository.findAll().stream()
                .sorted(Comparator.comparing(UserReport::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public List<AdminDtos.ReviewQueueItem> reviews(Boolean visible) {
        return feedbackRepository.findByReviewTypeOrderByCreatedAtDesc("INTERVIEWER_REVIEW").stream()
                .filter(item -> visible == null || visible.equals(Boolean.TRUE.equals(item.getPublicReview())))
                .map(this::toReviewQueueItem)
                .toList();
    }

    public User updateUserModeration(String userId, AdminDtos.UserModerationRequest request) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (request.getEnabled() != null) {
            user.setAccountEnabled(request.getEnabled());
        }
        if (request.getPublicProfileVisible() != null) {
            user.setPublicProfileVisible(request.getPublicProfileVisible());
        }
        return userRepository.save(user);
    }

    public User verifyInterviewer(String userId, AdminDtos.InterviewerVerificationRequest request) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (!user.hasRole("INTERVIEWER")) {
            throw new IllegalArgumentException("Only interviewer accounts can be verified");
        }
        user.setInterviewerVerified(request.getVerified() == null || request.getVerified());
        return userRepository.save(user);
    }

    public UserReport moderateReport(String reportId, AdminDtos.ReportModerationRequest request, String adminId) {
        UserReport report = userReportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found"));
        String status = normalize(request.getStatus());
        if (status == null) {
            throw new IllegalArgumentException("Report status is required");
        }
        report.setStatus(status.toUpperCase(Locale.ROOT));
        report.setResolutionNotes(normalize(request.getResolutionNotes()));
        report.setReviewedByAdminId(adminId);
        report.setUpdatedAt(Instant.now());
        return userReportRepository.save(report);
    }

    public AdminDtos.ReviewQueueItem moderateReview(String reviewId, AdminDtos.ReviewModerationRequest request, String adminId) {
        Feedback review = feedbackRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found"));
        if (!"INTERVIEWER_REVIEW".equalsIgnoreCase(normalize(review.getReviewType()))) {
            throw new IllegalArgumentException("Only public interviewer reviews can be moderated");
        }
        if (request != null && request.getVisible() != null) {
            review.setPublicReview(request.getVisible());
        }
        review.setModerationNotes(normalize(request == null ? null : request.getModerationNotes()));
        review.setModeratedByAdminId(adminId);
        review.setModeratedAt(Instant.now());
        return toReviewQueueItem(feedbackRepository.save(review));
    }

    private boolean matchesUserQuery(User user, String query) {
        return List.of(user.getUsername(), user.getDisplayName(), user.getEmail(), user.getCompany(), user.getCurrentRole()).stream()
                .filter(value -> value != null && !value.isBlank())
                .anyMatch(value -> value.toLowerCase(Locale.ROOT).contains(query));
    }

    private AdminDtos.ReviewQueueItem toReviewQueueItem(Feedback review) {
        return new AdminDtos.ReviewQueueItem(
                review.getId(),
                userRepository.findById(review.getReviewerId()).map(User::getDisplayName).orElse("InterviewPrep member"),
                userRepository.findById(review.getInterviewerId()).map(User::getDisplayName).orElse("Interviewer"),
                sessionRepository.findById(review.getSessionId()).map(session -> session.getTitle() == null ? "Interview session" : session.getTitle()).orElse("Interview session"),
                review.getRating(),
                review.getComments(),
                review.getCreatedAt() == null ? null : review.getCreatedAt().toString(),
                review.getPublicReview(),
                review.getModerationNotes(),
                review.getTopicFeedback().stream()
                        .map(topic -> new AdminDtos.ReviewTopicSummary(topic.getTopic(), topic.getRating()))
                        .toList()
        );
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private String normalizeRole(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private Instant sessionSortValue(Session session) {
        try {
            return session.getStartTime() == null ? Instant.EPOCH : OffsetDateTime.parse(session.getStartTime()).toInstant();
        } catch (DateTimeParseException ex) {
            return session.getUpdatedAt() == null ? Instant.EPOCH : session.getUpdatedAt();
        }
    }
}
