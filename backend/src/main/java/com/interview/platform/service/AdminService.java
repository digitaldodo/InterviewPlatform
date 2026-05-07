package com.interview.platform.service;

import com.interview.platform.dto.AdminDtos;
import com.interview.platform.exception.ResourceNotFoundException;
import com.interview.platform.model.Feedback;
import com.interview.platform.model.ModerationAuditLog;
import com.interview.platform.model.Session;
import com.interview.platform.model.User;
import com.interview.platform.model.UserReport;
import com.interview.platform.repository.FeedbackRepository;
import com.interview.platform.repository.ModerationAuditLogRepository;
import com.interview.platform.repository.SessionRepository;
import com.interview.platform.repository.UserReportRepository;
import com.interview.platform.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AdminService {
    private static final Set<String> REPORT_STATUSES = Set.of("OPEN", "REVIEWED", "ACTIONED", "DISMISSED", "DUPLICATE");
    private static final Set<String> VERIFICATION_STATUSES = Set.of("NONE", "PENDING", "APPROVED", "REJECTED");

    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final UserReportRepository userReportRepository;
    private final FeedbackRepository feedbackRepository;
    private final ModerationAuditService moderationAuditService;
    private final ModerationAuditLogRepository moderationAuditLogRepository;
    private final TrustSignalService trustSignalService;

    public AdminService(UserRepository userRepository,
                        SessionRepository sessionRepository,
                        UserReportRepository userReportRepository,
                        FeedbackRepository feedbackRepository,
                        ModerationAuditService moderationAuditService,
                        ModerationAuditLogRepository moderationAuditLogRepository,
                        TrustSignalService trustSignalService) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.userReportRepository = userReportRepository;
        this.feedbackRepository = feedbackRepository;
        this.moderationAuditService = moderationAuditService;
        this.moderationAuditLogRepository = moderationAuditLogRepository;
        this.trustSignalService = trustSignalService;
    }

    public AdminDtos.OverviewResponse overview() {
        List<User> users = userRepository.findAll();
        List<Session> sessions = sessionRepository.findAll();
        List<Feedback> reviews = feedbackRepository.findAll();
        List<UserReport> reports = userReportRepository.findAll();
        List<Feedback> publicReviews = reviews.stream()
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
        long openReports = reports.stream().filter(report -> "OPEN".equalsIgnoreCase(report.getStatus())).count();
        long visiblePublicReviews = publicReviews.stream().filter(item -> Boolean.TRUE.equals(item.getPublicReview())).count();
        long hiddenPublicReviews = reviews.stream()
                .filter(item -> "INTERVIEWER_REVIEW".equalsIgnoreCase(item.getReviewType()))
                .filter(item -> !Boolean.TRUE.equals(item.getPublicReview()))
                .count();
        long flaggedReviews = reviews.stream().filter(item -> Boolean.TRUE.equals(item.getFlaggedForModeration())).count();
        long pendingVerificationRequests = users.stream().filter(user -> "PENDING".equalsIgnoreCase(user.getVerificationRequestStatus())).count();
        double averageRating = Math.round(publicReviews.stream().mapToInt(Feedback::getRating).average().orElse(0.0) * 10.0) / 10.0;
        double completionRate = totalSessions == 0 ? 0.0 : Math.round((completedSessions * 1000.0) / totalSessions) / 10.0;
        double cancellationRate = totalSessions == 0 ? 0.0 : Math.round((cancelledSessions * 1000.0) / totalSessions) / 10.0;
        double averageReviewQualityScore = Math.round(reviews.stream().mapToDouble(Feedback::getReviewQualityScore).average().orElse(0.0) * 1000.0) / 10.0;
        double averageTrustScore = Math.round(users.stream()
                .mapToDouble(user -> trustSignalService.evaluate(user, sessions, reviews, reports).trustScore())
                .average()
                .orElse(0.0) * 10.0) / 10.0;

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
                flaggedReviews,
                pendingVerificationRequests,
                averageRating,
                completionRate,
                cancellationRate,
                averageReviewQualityScore,
                averageTrustScore,
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
        List<User> users = userRepository.findAll();
        List<Session> sessions = sessionRepository.findAll();
        List<Feedback> allReviews = feedbackRepository.findByReviewTypeOrderByCreatedAtDesc("INTERVIEWER_REVIEW");
        List<UserReport> reports = userReportRepository.findAll();
        Map<String, User> userIndex = users.stream().collect(Collectors.toMap(User::getId, user -> user, (left, right) -> left));
        Map<String, Session> sessionIndex = sessions.stream().collect(Collectors.toMap(Session::getId, session -> session, (left, right) -> left));
        return allReviews.stream()
                .filter(item -> visible == null || visible.equals(Boolean.TRUE.equals(item.getPublicReview())))
                .sorted(Comparator
                        .comparing((Feedback item) -> !Boolean.TRUE.equals(item.getFlaggedForModeration()))
                        .thenComparing(Feedback::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(review -> toReviewQueueItem(review, userIndex, sessionIndex, sessions, allReviews, reports))
                .toList();
    }

    public AdminDtos.TrustDashboardResponse trustDashboard() {
        List<User> users = userRepository.findAll();
        List<Session> sessions = sessionRepository.findAll();
        List<Feedback> reviews = feedbackRepository.findAll();
        List<UserReport> reports = userReportRepository.findAll();
        List<ModerationAuditLog> recentModeration = moderationAuditLogRepository.findTop20ByOrderByCreatedAtDesc();
        Map<String, User> userIndex = users.stream().collect(Collectors.toMap(User::getId, user -> user, (left, right) -> left));
        Map<String, Session> sessionIndex = sessions.stream().collect(Collectors.toMap(Session::getId, session -> session, (left, right) -> left));

        List<AdminDtos.FlaggedUserItem> flaggedUsers = users.stream()
                .map(user -> {
                    TrustSignalService.UserTrustSnapshot snapshot = trustSignalService.evaluate(user, sessions, reviews, reports);
                    return new AdminDtos.FlaggedUserItem(
                            user.getId(),
                            user.getDisplayName(),
                            user.getEmail(),
                            user.getVerificationRequestStatus(),
                            snapshot.trustScore(),
                            snapshot.sessionCompletionRate(),
                            snapshot.cancellationReliability(),
                            snapshot.responseConsistencyScore(),
                            snapshot.reviewQualityScore(),
                            snapshot.indicators()
                    );
                })
                .filter(item -> item.trustScore() < 85.0 || !item.indicators().isEmpty())
                .sorted(Comparator.comparing(AdminDtos.FlaggedUserItem::trustScore))
                .limit(20)
                .toList();

        List<AdminDtos.ReviewQueueItem> flaggedReviews = reviews.stream()
                .filter(item -> "INTERVIEWER_REVIEW".equalsIgnoreCase(item.getReviewType()))
                .filter(item -> Boolean.TRUE.equals(item.getFlaggedForModeration()) || !Boolean.TRUE.equals(item.getPublicReview()))
                .sorted(Comparator.comparing(Feedback::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(15)
                .map(review -> toReviewQueueItem(review, userIndex, sessionIndex, sessions, reviews, reports))
                .toList();

        List<AdminDtos.VerificationQueueItem> verificationQueue = users.stream()
                .filter(user -> user.hasRole("INTERVIEWER"))
                .filter(user -> "PENDING".equalsIgnoreCase(user.getVerificationRequestStatus()) || "REJECTED".equalsIgnoreCase(user.getVerificationRequestStatus()))
                .sorted(Comparator.comparing(User::getVerificationRequestedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toVerificationQueueItem)
                .toList();

        double averageReviewQualityScore = Math.round(reviews.stream().mapToDouble(Feedback::getReviewQualityScore).average().orElse(0.0) * 1000.0) / 10.0;
        double averageTrustScore = Math.round(users.stream()
                .mapToDouble(user -> trustSignalService.evaluate(user, sessions, reviews, reports).trustScore())
                .average()
                .orElse(0.0) * 10.0) / 10.0;

        return new AdminDtos.TrustDashboardResponse(
                reviews.stream().filter(item -> Boolean.TRUE.equals(item.getFlaggedForModeration())).count(),
                reports.stream().filter(item -> "OPEN".equalsIgnoreCase(item.getStatus())).count(),
                verificationQueue.stream().filter(item -> "PENDING".equalsIgnoreCase(item.status())).count(),
                flaggedUsers.size(),
                averageReviewQualityScore,
                averageTrustScore,
                flaggedUsers,
                flaggedReviews,
                verificationQueue,
                recentModeration.stream().map(this::toAuditItem).toList()
        );
    }

    public AdminDtos.AuditLogPage auditLogs(Integer page, Integer size, String entityType, String subjectUserId) {
        int safePage = Math.max(0, page == null ? 0 : page);
        int safeSize = Math.max(1, Math.min(50, size == null ? 10 : size));
        PageRequest request = PageRequest.of(safePage, safeSize);
        Page<ModerationAuditLog> result;
        if (normalize(subjectUserId) != null) {
            result = moderationAuditLogRepository.findBySubjectUserIdOrderByCreatedAtDesc(subjectUserId.trim(), request);
        } else if (normalize(entityType) != null) {
            result = moderationAuditLogRepository.findByEntityTypeOrderByCreatedAtDesc(entityType.trim().toUpperCase(Locale.ROOT), request);
        } else {
            result = moderationAuditLogRepository.findAllByOrderByCreatedAtDesc(request);
        }
        return new AdminDtos.AuditLogPage(
                result.getContent().stream().map(this::toAuditItem).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    public User updateUserModeration(String userId, AdminDtos.UserModerationRequest request, String adminId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Map<String, Object> before = stateMap(
                "enabled", user.getAccountEnabled(),
                "publicProfileVisible", user.getPublicProfileVisible()
        );
        boolean changed = false;
        if (request.getEnabled() != null && !request.getEnabled().equals(user.getAccountEnabled())) {
            user.setAccountEnabled(request.getEnabled());
            changed = true;
        }
        if (request.getPublicProfileVisible() != null && !request.getPublicProfileVisible().equals(user.getPublicProfileVisible())) {
            user.setPublicProfileVisible(request.getPublicProfileVisible());
            changed = true;
        }
        if (!changed) {
            return user;
        }
        String reason = requireReason(request.getReason(), "Moderation reason is required");
        User saved = userRepository.save(user);
        moderationAuditService.log(
                "USER",
                saved.getId(),
                adminId,
                saved.getId(),
                "USER_MODERATION_UPDATED",
                reason,
                "Updated user moderation controls",
                before,
                stateMap(
                        "enabled", saved.getAccountEnabled(),
                        "publicProfileVisible", saved.getPublicProfileVisible()
                )
        );
        return saved;
    }

    public User verifyInterviewer(String userId, AdminDtos.InterviewerVerificationRequest request, String adminId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (!user.hasRole("INTERVIEWER")) {
            throw new IllegalArgumentException("Only interviewer accounts can be verified");
        }
        String status = resolveVerificationStatus(request);
        String reason = requireReason(request.getReason(), "Verification reason is required");
        Map<String, Object> before = stateMap(
                "verified", user.getInterviewerVerified(),
                "status", user.getVerificationRequestStatus(),
                "notes", user.getVerificationNotes()
        );
        Instant now = Instant.now();
        user.setVerificationRequestStatus(status);
        user.setVerificationNotes(normalize(request.getNotes()));
        user.setVerificationReviewedAt(now);
        if ("APPROVED".equals(status)) {
            user.setInterviewerVerified(true);
            user.setVerificationApprovedAt(now);
        } else {
            user.setInterviewerVerified(false);
            user.setVerificationApprovedAt(null);
        }
        User saved = userRepository.save(user);
        moderationAuditService.log(
                "VERIFICATION",
                saved.getId(),
                adminId,
                saved.getId(),
                "VERIFICATION_" + status,
                reason,
                "Updated interviewer verification workflow",
                before,
                stateMap(
                        "verified", saved.getInterviewerVerified(),
                        "status", saved.getVerificationRequestStatus(),
                        "notes", saved.getVerificationNotes()
                )
        );
        return saved;
    }

    public UserReport moderateReport(String reportId, AdminDtos.ReportModerationRequest request, String adminId) {
        UserReport report = userReportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found"));
        String status = normalize(request.getStatus());
        if (status == null || !REPORT_STATUSES.contains(status.toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Choose a valid report status");
        }
        String reason = requireReason(request.getReason(), "Moderation reason is required");
        Map<String, Object> before = stateMap(
                "status", report.getStatus(),
                "resolutionNotes", report.getResolutionNotes()
        );
        report.setStatus(status.toUpperCase(Locale.ROOT));
        report.setResolutionNotes(normalize(request.getResolutionNotes()));
        report.setReviewedByAdminId(adminId);
        report.setModeratedAt(Instant.now());
        report.setUpdatedAt(Instant.now());
        UserReport saved = userReportRepository.save(report);
        moderationAuditService.log(
                "REPORT",
                saved.getId(),
                adminId,
                saved.getReportedUserId(),
                "REPORT_" + saved.getStatus(),
                reason,
                "Moderated user report",
                before,
                stateMap(
                        "status", saved.getStatus(),
                        "resolutionNotes", saved.getResolutionNotes()
                )
        );
        return saved;
    }

    public AdminDtos.ReviewQueueItem moderateReview(String reviewId, AdminDtos.ReviewModerationRequest request, String adminId) {
        Feedback review = feedbackRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found"));
        if (!"INTERVIEWER_REVIEW".equalsIgnoreCase(normalize(review.getReviewType()))) {
            throw new IllegalArgumentException("Only public interviewer reviews can be moderated");
        }
        String reason = requireReason(request == null ? null : request.getReason(), "Moderation reason is required");
        Map<String, Object> before = stateMap(
                "publicReview", review.getPublicReview(),
                "moderationNotes", review.getModerationNotes()
        );
        if (request != null && request.getVisible() != null) {
            review.setPublicReview(request.getVisible());
        }
        review.setModerationNotes(normalize(request == null ? null : request.getModerationNotes()));
        review.setModeratedByAdminId(adminId);
        review.setModeratedAt(Instant.now());
        Feedback saved = feedbackRepository.save(review);
        moderationAuditService.log(
                "REVIEW",
                saved.getId(),
                adminId,
                saved.getInterviewerId(),
                Boolean.TRUE.equals(saved.getPublicReview()) ? "REVIEW_PUBLISHED" : "REVIEW_HIDDEN",
                reason,
                "Moderated public review visibility",
                before,
                stateMap(
                        "publicReview", saved.getPublicReview(),
                        "moderationNotes", saved.getModerationNotes()
                )
        );
        List<User> users = userRepository.findAll();
        List<Session> sessions = sessionRepository.findAll();
        List<Feedback> reviews = feedbackRepository.findAll();
        List<UserReport> reports = userReportRepository.findAll();
        Map<String, User> userIndex = users.stream().collect(Collectors.toMap(User::getId, user -> user, (left, right) -> left));
        Map<String, Session> sessionIndex = sessions.stream().collect(Collectors.toMap(Session::getId, session -> session, (left, right) -> left));
        return toReviewQueueItem(saved, userIndex, sessionIndex, sessions, reviews, reports);
    }

    private boolean matchesUserQuery(User user, String query) {
        return List.of(user.getUsername(), user.getDisplayName(), user.getEmail(), user.getCompany(), user.getCurrentRole()).stream()
                .filter(value -> value != null && !value.isBlank())
                .anyMatch(value -> value.toLowerCase(Locale.ROOT).contains(query));
    }

    private AdminDtos.ReviewQueueItem toReviewQueueItem(Feedback review,
                                                        Map<String, User> userIndex,
                                                        Map<String, Session> sessionIndex,
                                                        List<Session> sessions,
                                                        List<Feedback> allReviews,
                                                        List<UserReport> reports) {
        User interviewer = userIndex.get(review.getInterviewerId());
        TrustSignalService.UserTrustSnapshot snapshot = interviewer == null
                ? null
                : trustSignalService.evaluate(interviewer, sessions, allReviews, reports);
        Session session = sessionIndex.get(review.getSessionId());
        return new AdminDtos.ReviewQueueItem(
                review.getId(),
                userIndex.get(review.getReviewerId()) == null ? "InterviewPrep member" : userIndex.get(review.getReviewerId()).getDisplayName(),
                interviewer == null ? "Interviewer" : interviewer.getDisplayName(),
                session == null ? "Interview session" : (session.getTitle() == null ? "Interview session" : session.getTitle()),
                review.getRating(),
                review.getComments(),
                review.getCreatedAt() == null ? null : review.getCreatedAt().toString(),
                review.getPublicReview(),
                review.getFlaggedForModeration(),
                review.getSuspiciousFlags(),
                review.getSuspiciousScore(),
                review.getReviewQualityScore(),
                review.getModerationNotes(),
                interviewer != null && Boolean.TRUE.equals(interviewer.getInterviewerVerified()),
                snapshot == null ? null : snapshot.trustScore(),
                interviewer == null ? null : interviewer.getCancelledSessions(),
                review.getTopicFeedback().stream()
                        .map(topic -> new AdminDtos.ReviewTopicSummary(
                                topic.getTopic(),
                                topic.getRating(),
                                topic.getSkillRatings(),
                                topic.getStrengths(),
                                topic.getImprovementAreas(),
                                topic.getComments()
                        ))
                        .toList()
        );
    }

    private AdminDtos.VerificationQueueItem toVerificationQueueItem(User user) {
        return new AdminDtos.VerificationQueueItem(
                user.getId(),
                user.getDisplayName(),
                user.getEmail(),
                user.getLinkedInUrl(),
                user.getVerificationCompanyEmail(),
                user.getVerificationRequestStatus(),
                user.getVerificationRequestNotes(),
                user.getVerificationNotes(),
                user.getVerificationRequestedAt() == null ? null : user.getVerificationRequestedAt().toString(),
                user.getVerificationReviewedAt() == null ? null : user.getVerificationReviewedAt().toString()
        );
    }

    private AdminDtos.ModerationAuditItem toAuditItem(ModerationAuditLog log) {
        return new AdminDtos.ModerationAuditItem(
                log.getId(),
                log.getEntityType(),
                log.getEntityId(),
                log.getAction(),
                log.getActorUserId(),
                log.getSubjectUserId(),
                log.getReason(),
                log.getSummary(),
                log.getCreatedAt() == null ? null : log.getCreatedAt().toString()
        );
    }

    private String requireReason(String value, String message) {
        String normalized = normalize(value);
        if (normalized == null || normalized.length() < 6) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private String resolveVerificationStatus(AdminDtos.InterviewerVerificationRequest request) {
        String explicitStatus = normalize(request == null ? null : request.getStatus());
        if (explicitStatus != null) {
            String value = explicitStatus.toUpperCase(Locale.ROOT);
            if (!VERIFICATION_STATUSES.contains(value) || "NONE".equals(value)) {
                throw new IllegalArgumentException("Choose a valid verification status");
            }
            return value;
        }
        if (request != null && request.getVerified() != null) {
            return request.getVerified() ? "APPROVED" : "REJECTED";
        }
        throw new IllegalArgumentException("Choose a valid verification status");
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private String normalizeRole(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private Map<String, Object> stateMap(Object... pairs) {
        Map<String, Object> values = new HashMap<>();
        for (int index = 0; index + 1 < pairs.length; index += 2) {
            values.put(String.valueOf(pairs[index]), pairs[index + 1]);
        }
        return values;
    }

    private Instant sessionSortValue(Session session) {
        try {
            return session.getStartTime() == null ? Instant.EPOCH : OffsetDateTime.parse(session.getStartTime()).toInstant();
        } catch (DateTimeParseException ex) {
            return session.getUpdatedAt() == null ? Instant.EPOCH : session.getUpdatedAt();
        }
    }
}
