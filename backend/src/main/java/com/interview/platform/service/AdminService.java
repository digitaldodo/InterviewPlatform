package com.interview.platform.service;

import com.interview.platform.dto.AdminDtos;
import com.interview.platform.dto.PageResponse;
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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
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
        List<Feedback> publicReviews = reviews.stream().filter(item -> Boolean.TRUE.equals(item.getPublicReview())).toList();

        long totalUsers = users.size();
        long totalInterviewers = users.stream().filter(user -> user.hasRole("INTERVIEWER")).count();
        long totalAdmins = users.stream().filter(user -> user.hasRole("ADMIN")).count();
        long enabledUsers = users.stream().filter(user -> !Boolean.FALSE.equals(user.getAccountEnabled())).count();
        long verifiedInterviewers = users.stream().filter(user -> Boolean.TRUE.equals(user.getInterviewerVerified())).count();
        long activeUsers = users.stream().filter(this::isActiveUser).count();
        long totalSessions = sessions.size();
        long completedSessions = sessions.stream().filter(session -> "COMPLETED".equalsIgnoreCase(session.getStatus())).count();
        long cancelledSessions = sessions.stream().filter(session -> "CANCELLED".equalsIgnoreCase(session.getStatus())).count();
        long pendingSessions = sessions.stream().filter(session -> "PENDING".equalsIgnoreCase(session.getStatus()) || "CONFIRMED".equalsIgnoreCase(session.getStatus())).count();
        long openReports = reports.stream().filter(report -> "OPEN".equalsIgnoreCase(report.getStatus())).count();
        long visiblePublicReviews = publicReviews.size();
        long hiddenPublicReviews = reviews.stream()
                .filter(item -> "INTERVIEWER_REVIEW".equalsIgnoreCase(item.getReviewType()))
                .filter(item -> !Boolean.TRUE.equals(item.getPublicReview()))
                .count();
        long flaggedReviews = reviews.stream().filter(item -> Boolean.TRUE.equals(item.getFlaggedForModeration())).count();
        long pendingVerificationRequests = users.stream().filter(user -> "PENDING".equalsIgnoreCase(user.getVerificationRequestStatus())).count();
        long disputedSessions = reports.stream()
                .filter(report -> report.getSessionId() != null && !report.getSessionId().isBlank())
                .filter(report -> !"NO_SHOW".equals(resolveReportCategory(report)))
                .map(UserReport::getSessionId)
                .distinct()
                .count();
        long noShowSessions = reports.stream()
                .filter(report -> report.getSessionId() != null && !report.getSessionId().isBlank())
                .filter(report -> "NO_SHOW".equals(resolveReportCategory(report)))
                .map(UserReport::getSessionId)
                .distinct()
                .count();

        Map<String, TrustSignalService.UserTrustSnapshot> trustMap = evaluateTrust(users, sessions, reviews, reports);
        long flaggedUsers = trustMap.values().stream().filter(this::isFlaggedTrust).count();

        double averageRating = Math.round(publicReviews.stream().mapToInt(Feedback::getRating).average().orElse(0.0) * 10.0) / 10.0;
        double completionRate = totalSessions == 0 ? 0.0 : Math.round((completedSessions * 1000.0) / totalSessions) / 10.0;
        double cancellationRate = totalSessions == 0 ? 0.0 : Math.round((cancelledSessions * 1000.0) / totalSessions) / 10.0;
        double averageReviewQualityScore = Math.round(reviews.stream().mapToDouble(Feedback::getReviewQualityScore).average().orElse(0.0) * 1000.0) / 10.0;
        double averageTrustScore = Math.round(trustMap.values().stream().mapToDouble(TrustSignalService.UserTrustSnapshot::trustScore).average().orElse(0.0) * 10.0) / 10.0;

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

        List<AdminDtos.PlatformHealthIndicator> healthIndicators = buildHealthIndicators(
                totalSessions,
                openReports,
                cancelledSessions,
                pendingVerificationRequests,
                flaggedReviews,
                averageTrustScore
        );

        return new AdminDtos.OverviewResponse(
                totalUsers,
                activeUsers,
                totalInterviewers,
                totalAdmins,
                enabledUsers,
                verifiedInterviewers,
                flaggedUsers,
                totalSessions,
                completedSessions,
                cancelledSessions,
                pendingSessions,
                disputedSessions,
                noShowSessions,
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
                topTopics,
                healthIndicators
        );
    }

    public AdminDtos.AdminAnalyticsResponse analytics(Integer days) {
        int safeDays = Math.max(7, Math.min(90, days == null ? 30 : days));
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate start = today.minusDays(safeDays - 1L);

        List<User> users = userRepository.findAll();
        List<Session> sessions = sessionRepository.findAll();
        List<Feedback> reviews = feedbackRepository.findAll();
        List<UserReport> reports = userReportRepository.findAll();

        Map<LocalDate, Long> usersByDay = bucketCounts(users.stream().map(User::getCreatedAt).filter(Objects::nonNull).toList());
        Map<LocalDate, Long> sessionsByDay = bucketCounts(sessions.stream().map(session -> parseSessionInstant(session.getStartTime()).orElse(null)).filter(Objects::nonNull).toList());
        Map<LocalDate, Long> completedSessionsByDay = bucketCounts(sessions.stream()
                .filter(session -> "COMPLETED".equalsIgnoreCase(session.getStatus()))
                .map(session -> parseSessionInstant(session.getStartTime()).orElse(null))
                .filter(Objects::nonNull)
                .toList());
        Map<LocalDate, Long> cancelledSessionsByDay = bucketCounts(sessions.stream()
                .filter(session -> "CANCELLED".equalsIgnoreCase(session.getStatus()))
                .map(session -> parseSessionInstant(session.getStartTime()).orElse(null))
                .filter(Objects::nonNull)
                .toList());
        Map<LocalDate, Long> reviewsByDay = bucketCounts(reviews.stream().map(Feedback::getCreatedAt).filter(Objects::nonNull).toList());
        Map<LocalDate, Long> reportsByDay = bucketCounts(reports.stream().map(UserReport::getCreatedAt).filter(Objects::nonNull).toList());

        Map<LocalDate, List<Feedback>> ratingBuckets = reviews.stream()
                .filter(item -> item.getCreatedAt() != null)
                .collect(Collectors.groupingBy(item -> item.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate()));

        List<AdminDtos.TrendPoint> userGrowthTrend = new ArrayList<>();
        List<AdminDtos.TrendPoint> activeUserTrend = new ArrayList<>();
        List<AdminDtos.TrendPoint> sessionTrend = new ArrayList<>();
        List<AdminDtos.TrendPoint> completedSessionTrend = new ArrayList<>();
        List<AdminDtos.TrendPoint> cancellationTrend = new ArrayList<>();
        List<AdminDtos.TrendPoint> reviewTrend = new ArrayList<>();
        List<AdminDtos.TrendPoint> reportTrend = new ArrayList<>();
        List<AdminDtos.RateTrendPoint> averageRatingTrend = new ArrayList<>();
        List<AdminDtos.RateTrendPoint> trustTrend = new ArrayList<>();

        for (int offset = 0; offset < safeDays; offset += 1) {
            LocalDate day = start.plusDays(offset);
            String label = day.toString();
            long sessionsCount = sessionsByDay.getOrDefault(day, 0L);
            long reviewsCount = reviewsByDay.getOrDefault(day, 0L);
            long reportsCount = reportsByDay.getOrDefault(day, 0L);
            long cancelledCount = cancelledSessionsByDay.getOrDefault(day, 0L);
            long flaggedReviewsCount = reviews.stream()
                    .filter(item -> item.getCreatedAt() != null)
                    .filter(item -> item.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate().equals(day))
                    .filter(item -> Boolean.TRUE.equals(item.getFlaggedForModeration()))
                    .count();
            long noShowReportsCount = reports.stream()
                    .filter(report -> report.getCreatedAt() != null)
                    .filter(report -> report.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate().equals(day))
                    .filter(report -> "NO_SHOW".equals(resolveReportCategory(report)))
                    .count();
            long dailyActivity = Math.max(1L, sessionsCount + reviewsCount);
            double trustScore = Math.max(0.0, 100.0 - (((flaggedReviewsCount + noShowReportsCount) * 100.0) / dailyActivity));
            double dayAverageRating = Math.round(ratingBuckets.getOrDefault(day, List.of()).stream()
                    .mapToInt(Feedback::getRating)
                    .average()
                    .orElse(0.0) * 10.0) / 10.0;

            userGrowthTrend.add(new AdminDtos.TrendPoint(label, usersByDay.getOrDefault(day, 0L)));
            activeUserTrend.add(new AdminDtos.TrendPoint(label, countActiveUsersForDay(users, sessions, day)));
            sessionTrend.add(new AdminDtos.TrendPoint(label, sessionsCount));
            completedSessionTrend.add(new AdminDtos.TrendPoint(label, completedSessionsByDay.getOrDefault(day, 0L)));
            cancellationTrend.add(new AdminDtos.TrendPoint(label, cancelledCount));
            reviewTrend.add(new AdminDtos.TrendPoint(label, reviewsCount));
            reportTrend.add(new AdminDtos.TrendPoint(label, reportsCount));
            averageRatingTrend.add(new AdminDtos.RateTrendPoint(label, dayAverageRating));
            trustTrend.add(new AdminDtos.RateTrendPoint(label, Math.round(trustScore * 10.0) / 10.0));
        }

        Map<String, TrustSignalService.UserTrustSnapshot> trustMap = evaluateTrust(users, sessions, reviews, reports);
        long flaggedUsers = trustMap.values().stream().filter(this::isFlaggedTrust).count();
        long disputedSessions = reports.stream()
                .filter(report -> report.getSessionId() != null && !report.getSessionId().isBlank())
                .filter(report -> !"NO_SHOW".equals(resolveReportCategory(report)))
                .map(UserReport::getSessionId)
                .distinct()
                .count();
        long noShowSessions = reports.stream()
                .filter(report -> report.getSessionId() != null && !report.getSessionId().isBlank())
                .filter(report -> "NO_SHOW".equals(resolveReportCategory(report)))
                .map(UserReport::getSessionId)
                .distinct()
                .count();

        return new AdminDtos.AdminAnalyticsResponse(
                safeDays,
                userGrowthTrend,
                activeUserTrend,
                sessionTrend,
                completedSessionTrend,
                cancellationTrend,
                reviewTrend,
                reportTrend,
                averageRatingTrend,
                trustTrend,
                users.stream().filter(this::isActiveUser).count(),
                flaggedUsers,
                disputedSessions,
                noShowSessions
        );
    }

    public PageResponse<AdminDtos.AdminUserItem> users(String q,
                                                       String role,
                                                       Boolean enabled,
                                                       String verification,
                                                       Boolean flagged,
                                                       String sortBy,
                                                       String sortDir,
                                                       Integer page,
                                                       Integer size) {
        List<User> users = userRepository.findAll();
        String query = normalize(q);
        String normalizedRole = normalizeRole(role);
        String normalizedVerification = normalize(verification) == null ? null : normalize(verification).toUpperCase(Locale.ROOT);

        boolean trustNeeded = Boolean.TRUE.equals(flagged) || "TRUST".equalsIgnoreCase(sortBy);
        List<Session> sessions = trustNeeded ? sessionRepository.findAll() : List.of();
        List<Feedback> reviews = trustNeeded ? feedbackRepository.findAll() : List.of();
        List<UserReport> reports = trustNeeded ? userReportRepository.findAll() : List.of();
        Map<String, TrustSignalService.UserTrustSnapshot> trustMap = trustNeeded ? evaluateTrust(users, sessions, reviews, reports) : new HashMap<>();

        Map<String, Long> moderationCountMap = moderationAuditLogRepository.findAll().stream()
                .filter(log -> log.getSubjectUserId() != null && !log.getSubjectUserId().isBlank())
                .collect(Collectors.groupingBy(ModerationAuditLog::getSubjectUserId, Collectors.counting()));

        List<User> filtered = users.stream()
                .filter(user -> query == null || matchesUserQuery(user, query))
                .filter(user -> normalizedRole == null || user.hasRole(normalizedRole))
                .filter(user -> enabled == null || enabled.equals(!Boolean.FALSE.equals(user.getAccountEnabled())))
                .filter(user -> normalizedVerification == null || normalizedVerification.equalsIgnoreCase(user.getVerificationRequestStatus()))
                .filter(user -> !Boolean.TRUE.equals(flagged) || isFlaggedTrust(trustMap.get(user.getId())))
                .toList();

        Comparator<User> comparator = userComparator(sortBy, trustMap);
        if ("ASC".equalsIgnoreCase(normalize(sortDir))) {
            filtered = filtered.stream().sorted(comparator).toList();
        } else {
            filtered = filtered.stream().sorted(comparator.reversed()).toList();
        }

        List<AdminDtos.AdminUserItem> items = filtered.stream().map(user -> {
            TrustSignalService.UserTrustSnapshot snapshot = trustMap.get(user.getId());
            if (snapshot == null) {
                snapshot = trustSignalService.evaluate(user, List.of(), List.of(), List.of());
            }
            return new AdminDtos.AdminUserItem(
                    user.getId(),
                    user.getDisplayName(),
                    user.getEmail(),
                    user.getRoles(),
                    user.getAccountEnabled(),
                    user.getPublicProfileVisible(),
                    user.getInterviewerVerified(),
                    user.getVerificationRequestStatus(),
                    user.getCompany(),
                    user.getCurrentRole(),
                    toIso(user.getCreatedAt()),
                    toIso(user.getLastLogin()),
                    snapshot.trustScore(),
                    isFlaggedTrust(snapshot),
                    moderationCountMap.getOrDefault(user.getId(), 0L)
            );
        }).toList();

        return paginate(items, page, size);
    }

    public PageResponse<AdminDtos.AdminSessionItem> sessions(String q,
                                                             String status,
                                                             Boolean cancellationOnly,
                                                             Boolean noShowOnly,
                                                             Boolean disputedOnly,
                                                             String sortBy,
                                                             String sortDir,
                                                             Integer page,
                                                             Integer size) {
        String query = normalize(q);
        String normalizedStatus = normalize(status) == null ? null : normalize(status).toUpperCase(Locale.ROOT);

        List<Session> sessions = sessionRepository.findAll();
        Map<String, User> users = userRepository.findAll().stream().collect(Collectors.toMap(User::getId, Function.identity(), (left, right) -> left));
        Map<String, List<UserReport>> reportsBySession = userReportRepository.findAll().stream()
                .filter(item -> item.getSessionId() != null && !item.getSessionId().isBlank())
                .collect(Collectors.groupingBy(UserReport::getSessionId));

        List<Session> filtered = sessions.stream()
                .filter(session -> normalizedStatus == null || normalizedStatus.equalsIgnoreCase(session.getStatus()))
                .filter(session -> {
                    User interviewer = users.get(session.getInterviewerId());
                    User candidate = users.get(session.getCandidateId());
                    String searchText = String.join(" ",
                            safe(sessionTitle(session)),
                            safe(String.join(", ", session.getTopics())),
                            safe(session.getMeetingProvider()),
                            safe(interviewer == null ? null : interviewer.getDisplayName()),
                            safe(candidate == null ? null : candidate.getDisplayName()),
                            safe(session.getStatus())
                    ).toLowerCase(Locale.ROOT);
                    return query == null || searchText.contains(query.toLowerCase(Locale.ROOT));
                })
                .filter(session -> {
                    SessionRisk risk = sessionRisk(session, reportsBySession.getOrDefault(session.getId(), List.of()));
                    if (Boolean.TRUE.equals(cancellationOnly) && !risk.cancellation()) return false;
                    if (Boolean.TRUE.equals(noShowOnly) && !risk.noShowRisk()) return false;
                    if (Boolean.TRUE.equals(disputedOnly) && !risk.disputeRisk()) return false;
                    return true;
                })
                .toList();

        Comparator<Session> comparator = sessionComparator(sortBy, reportsBySession);
        if ("ASC".equalsIgnoreCase(normalize(sortDir))) {
            filtered = filtered.stream().sorted(comparator).toList();
        } else {
            filtered = filtered.stream().sorted(comparator.reversed()).toList();
        }

        List<AdminDtos.AdminSessionItem> items = filtered.stream().map(session -> {
            User interviewer = users.get(session.getInterviewerId());
            User candidate = users.get(session.getCandidateId());
            List<UserReport> relatedReports = reportsBySession.getOrDefault(session.getId(), List.of());
            SessionRisk risk = sessionRisk(session, relatedReports);
            String lastStatus = relatedReports.stream()
                    .sorted(Comparator.comparing(UserReport::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                    .map(UserReport::getStatus)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
            long openCount = relatedReports.stream().filter(report -> "OPEN".equalsIgnoreCase(report.getStatus())).count();

            return new AdminDtos.AdminSessionItem(
                    session.getId(),
                    sessionTitle(session),
                    session.getStatus(),
                    session.getStartTime(),
                    session.getDurationMinutes(),
                    session.getMeetingProvider(),
                    session.getInterviewerId(),
                    interviewer == null ? "Interviewer" : interviewer.getDisplayName(),
                    session.getCandidateId(),
                    candidate == null ? "Candidate" : candidate.getDisplayName(),
                    session.getTopics(),
                    risk.cancellation(),
                    risk.noShowRisk(),
                    risk.disputeRisk(),
                    (long) relatedReports.size(),
                    openCount,
                    lastStatus
            );
        }).toList();

        return paginate(items, page, size);
    }

    public PageResponse<AdminDtos.AdminReportItem> reports(String q,
                                                           String status,
                                                           String category,
                                                           String sortBy,
                                                           String sortDir,
                                                           Integer page,
                                                           Integer size) {
        String query = normalize(q);
        String normalizedStatus = normalize(status) == null ? null : normalize(status).toUpperCase(Locale.ROOT);
        String normalizedCategory = normalize(category) == null ? null : normalize(category).toUpperCase(Locale.ROOT);

        Map<String, User> users = userRepository.findAll().stream().collect(Collectors.toMap(User::getId, Function.identity(), (left, right) -> left));
        List<UserReport> reports = userReportRepository.findAll().stream()
                .filter(report -> normalizedStatus == null || normalizedStatus.equalsIgnoreCase(report.getStatus()))
                .filter(report -> normalizedCategory == null || normalizedCategory.equals(resolveReportCategory(report)))
                .filter(report -> {
                    String text = String.join(" ",
                            safe(report.getReason()),
                            safe(report.getDetails()),
                            safe(report.getStatus()),
                            safe(report.getReporterId()),
                            safe(report.getReportedUserId()),
                            safe(report.getSessionId()),
                            safe(report.getReviewedByAdminId())
                    ).toLowerCase(Locale.ROOT);
                    return query == null || text.contains(query.toLowerCase(Locale.ROOT));
                })
                .toList();

        Comparator<UserReport> comparator = reportComparator(sortBy);
        if ("ASC".equalsIgnoreCase(normalize(sortDir))) {
            reports = reports.stream().sorted(comparator).toList();
        } else {
            reports = reports.stream().sorted(comparator.reversed()).toList();
        }

        List<AdminDtos.AdminReportItem> items = reports.stream().map(report -> {
            User reporter = users.get(report.getReporterId());
            User target = users.get(report.getReportedUserId());
            return new AdminDtos.AdminReportItem(
                    report.getId(),
                    resolveReportCategory(report),
                    report.getReason(),
                    report.getDetails(),
                    report.getStatus(),
                    report.getDuplicateCount(),
                    report.getReporterId(),
                    reporter == null ? null : reporter.getDisplayName(),
                    report.getReportedUserId(),
                    target == null ? null : target.getDisplayName(),
                    report.getSessionId(),
                    toIso(report.getCreatedAt()),
                    toIso(report.getModeratedAt()),
                    report.getReviewedByAdminId(),
                    report.getResolutionNotes()
            );
        }).toList();

        return paginate(items, page, size);
    }

    public PageResponse<AdminDtos.ReviewQueueItem> reviews(String q,
                                                           Boolean visible,
                                                           Integer minRating,
                                                           Boolean flaggedOnly,
                                                           String sortBy,
                                                           String sortDir,
                                                           Integer page,
                                                           Integer size) {
        List<User> users = userRepository.findAll();
        List<Session> sessions = sessionRepository.findAll();
        List<Feedback> allReviews = feedbackRepository.findByReviewTypeOrderByCreatedAtDesc("INTERVIEWER_REVIEW");
        List<UserReport> reports = userReportRepository.findAll();
        Map<String, User> userIndex = users.stream().collect(Collectors.toMap(User::getId, user -> user, (left, right) -> left));
        Map<String, Session> sessionIndex = sessions.stream().collect(Collectors.toMap(Session::getId, session -> session, (left, right) -> left));

        String query = normalize(q);
        int safeMinRating = minRating == null ? 0 : Math.max(0, minRating);

        List<Feedback> filtered = allReviews.stream()
                .filter(item -> visible == null || visible.equals(Boolean.TRUE.equals(item.getPublicReview())))
                .filter(item -> safeMinRating <= 0 || item.getRating() >= safeMinRating)
                .filter(item -> !Boolean.TRUE.equals(flaggedOnly) || Boolean.TRUE.equals(item.getFlaggedForModeration()) || item.getSuspiciousScore() >= 60)
                .filter(item -> {
                    if (query == null) return true;
                    String search = String.join(" ",
                            safe(item.getComments()),
                            safe(item.getModerationNotes()),
                            safe(userDisplayName(userIndex.get(item.getReviewerId()))),
                            safe(userDisplayName(userIndex.get(item.getInterviewerId()))),
                            safe(item.getSessionId())
                    ).toLowerCase(Locale.ROOT);
                    return search.contains(query.toLowerCase(Locale.ROOT));
                })
                .toList();

        Comparator<Feedback> comparator = reviewComparator(sortBy);
        if ("ASC".equalsIgnoreCase(normalize(sortDir))) {
            filtered = filtered.stream().sorted(comparator).toList();
        } else {
            filtered = filtered.stream().sorted(comparator.reversed()).toList();
        }

        List<AdminDtos.ReviewQueueItem> items = filtered.stream()
                .map(review -> toReviewQueueItem(review, userIndex, sessionIndex, sessions, allReviews, reports))
                .toList();

        return paginate(items, page, size);
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

    public AdminDtos.AuditLogPage auditLogs(Integer page,
                                            Integer size,
                                            String entityType,
                                            String subjectUserId,
                                            String actorUserId,
                                            String q) {
        int safePage = Math.max(0, page == null ? 0 : page);
        int safeSize = Math.max(1, Math.min(100, size == null ? 12 : size));

        String normalizedEntityType = normalize(entityType) == null ? null : normalize(entityType).toUpperCase(Locale.ROOT);
        String normalizedSubject = normalize(subjectUserId);
        String normalizedActor = normalize(actorUserId);
        String query = normalize(q);

        List<ModerationAuditLog> logs;
        if (query != null || normalizedActor != null) {
            logs = moderationAuditLogRepository.findAll().stream()
                    .sorted(Comparator.comparing(ModerationAuditLog::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                    .filter(log -> normalizedEntityType == null || normalizedEntityType.equalsIgnoreCase(log.getEntityType()))
                    .filter(log -> normalizedSubject == null || normalizedSubject.equalsIgnoreCase(log.getSubjectUserId()))
                    .filter(log -> normalizedActor == null || normalizedActor.equalsIgnoreCase(log.getActorUserId()))
                    .filter(log -> query == null || matchesAuditQuery(log, query))
                    .toList();
            return toAuditPage(logs, safePage, safeSize);
        }

        PageRequest request = PageRequest.of(safePage, safeSize);
        Page<ModerationAuditLog> result;
        if (normalizedSubject != null) {
            result = moderationAuditLogRepository.findBySubjectUserIdOrderByCreatedAtDesc(normalizedSubject, request);
        } else if (normalizedEntityType != null) {
            result = moderationAuditLogRepository.findByEntityTypeOrderByCreatedAtDesc(normalizedEntityType, request);
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
        } else if ("NONE".equals(status)) {
            user.setInterviewerVerified(false);
            user.setVerificationApprovedAt(null);
            user.setVerificationReviewedAt(null);
            user.setVerificationRequestedAt(null);
            user.setVerificationNotes(null);
            user.setVerificationRequestNotes(null);
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
        String lower = query.toLowerCase(Locale.ROOT);
        return List.of(user.getUsername(), user.getDisplayName(), user.getEmail(), user.getCompany(), user.getCurrentRole()).stream()
                .filter(value -> value != null && !value.isBlank())
                .anyMatch(value -> value.toLowerCase(Locale.ROOT).contains(lower));
    }

    private boolean isActiveUser(User user) {
        Instant threshold = Instant.now().minusSeconds(30L * 24L * 60L * 60L);
        if (user.getLastLogin() != null) {
            return user.getLastLogin().isAfter(threshold);
        }
        return user.getCreatedAt() != null && user.getCreatedAt().isAfter(threshold);
    }

    private long countActiveUsersForDay(List<User> users, List<Session> sessions, LocalDate day) {
        Set<String> activeIds = new HashSet<>();
        users.stream()
                .filter(user -> user.getLastLogin() != null)
                .filter(user -> user.getLastLogin().atZone(ZoneOffset.UTC).toLocalDate().equals(day))
                .map(User::getId)
                .filter(Objects::nonNull)
                .forEach(activeIds::add);
        sessions.stream()
                .filter(session -> parseSessionInstant(session.getStartTime()).map(instant -> instant.atZone(ZoneOffset.UTC).toLocalDate().equals(day)).orElse(false))
                .forEach(session -> {
                    if (session.getInterviewerId() != null) activeIds.add(session.getInterviewerId());
                    if (session.getCandidateId() != null) activeIds.add(session.getCandidateId());
                });
        return activeIds.size();
    }

    private Map<String, TrustSignalService.UserTrustSnapshot> evaluateTrust(List<User> users,
                                                                            List<Session> sessions,
                                                                            List<Feedback> reviews,
                                                                            List<UserReport> reports) {
        Map<String, TrustSignalService.UserTrustSnapshot> trustMap = new HashMap<>();
        for (User user : users) {
            trustMap.put(user.getId(), trustSignalService.evaluate(user, sessions, reviews, reports));
        }
        return trustMap;
    }

    private boolean isFlaggedTrust(TrustSignalService.UserTrustSnapshot snapshot) {
        return snapshot != null && (snapshot.trustScore() < 85.0 || (snapshot.indicators() != null && !snapshot.indicators().isEmpty()));
    }

    private Comparator<User> userComparator(String sortBy, Map<String, TrustSignalService.UserTrustSnapshot> trustMap) {
        String key = normalize(sortBy) == null ? "CREATED_AT" : normalize(sortBy).toUpperCase(Locale.ROOT);
        return switch (key) {
            case "NAME" -> Comparator.comparing(User::getDisplayName, Comparator.nullsLast(String::compareToIgnoreCase));
            case "LAST_LOGIN" -> Comparator.comparing(User::getLastLogin, Comparator.nullsLast(Comparator.naturalOrder()));
            case "TRUST" -> Comparator.comparing((User user) -> {
                TrustSignalService.UserTrustSnapshot snapshot = trustMap.get(user.getId());
                return snapshot == null ? 0.0 : snapshot.trustScore();
            }, Comparator.nullsLast(Comparator.naturalOrder()));
            case "RATING" -> Comparator.comparing(User::getAverageRating, Comparator.nullsLast(Comparator.naturalOrder()));
            case "SESSIONS" -> Comparator.comparing(User::getCompletedSessions, Comparator.nullsLast(Comparator.naturalOrder()));
            default -> Comparator.comparing(User::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
        };
    }

    private Comparator<Session> sessionComparator(String sortBy, Map<String, List<UserReport>> reportsBySession) {
        String key = normalize(sortBy) == null ? "START_TIME" : normalize(sortBy).toUpperCase(Locale.ROOT);
        return switch (key) {
            case "STATUS" -> Comparator.comparing(session -> normalize(session.getStatus()), Comparator.nullsLast(String::compareToIgnoreCase));
            case "REPORTS" -> Comparator.comparing((Session session) -> reportsBySession.getOrDefault(session.getId(), List.of()).size());
            default -> Comparator.comparing(this::sessionSortValue, Comparator.nullsLast(Comparator.naturalOrder()));
        };
    }

    private Comparator<UserReport> reportComparator(String sortBy) {
        String key = normalize(sortBy) == null ? "CREATED_AT" : normalize(sortBy).toUpperCase(Locale.ROOT);
        return switch (key) {
            case "STATUS" -> Comparator.comparing(UserReport::getStatus, Comparator.nullsLast(String::compareToIgnoreCase));
            case "DUPLICATES" -> Comparator.comparing(UserReport::getDuplicateCount, Comparator.nullsLast(Comparator.naturalOrder()));
            default -> Comparator.comparing(UserReport::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
        };
    }

    private Comparator<Feedback> reviewComparator(String sortBy) {
        String key = normalize(sortBy) == null ? "CREATED_AT" : normalize(sortBy).toUpperCase(Locale.ROOT);
        return switch (key) {
            case "RATING" -> Comparator.comparingInt(Feedback::getRating);
            case "RISK" -> Comparator.comparing(Feedback::getSuspiciousScore, Comparator.nullsLast(Comparator.naturalOrder()));
            case "QUALITY" -> Comparator.comparing(Feedback::getReviewQualityScore, Comparator.nullsLast(Comparator.naturalOrder()));
            default -> Comparator.comparing(Feedback::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
        };
    }

    private SessionRisk sessionRisk(Session session, List<UserReport> reports) {
        boolean cancellation = "CANCELLED".equalsIgnoreCase(session.getStatus());
        boolean noShowRisk = reports.stream().anyMatch(report -> "NO_SHOW".equals(resolveReportCategory(report)));
        boolean disputeRisk = reports.stream().anyMatch(report -> !"NO_SHOW".equals(resolveReportCategory(report)));

        if (!noShowRisk && parseSessionInstant(session.getStartTime()).isPresent()) {
            Instant start = parseSessionInstant(session.getStartTime()).get();
            boolean hasStarted = session.getMeetingStartedAt() != null;
            boolean ended = "COMPLETED".equalsIgnoreCase(session.getStatus()) || "CANCELLED".equalsIgnoreCase(session.getStatus());
            if (!hasStarted && ended && start.isBefore(Instant.now().minusSeconds(60L * 60L))) {
                noShowRisk = true;
            }
        }
        return new SessionRisk(cancellation, noShowRisk, disputeRisk);
    }

    private String resolveReportCategory(UserReport report) {
        String category = normalize(report.getCategory());
        if (category != null) return category.toUpperCase(Locale.ROOT);
        String reason = normalize(report.getReason());
        if (reason == null) return "OTHER";
        String upper = reason.toUpperCase(Locale.ROOT);
        if (upper.contains("NO SHOW") || upper.contains("NO-SHOW") || upper.contains("LATE")) return "NO_SHOW";
        if (upper.contains("SPAM") || upper.contains("SCAM")) return "SPAM";
        if (upper.contains("SAFETY") || upper.contains("HARASS") || upper.contains("ABUSE")) return "SAFETY";
        if (upper.contains("PROFILE") || upper.contains("FAKE")) return "PROFILE";
        if (upper.contains("QUALITY") || upper.contains("UNPROFESSIONAL")) return "QUALITY";
        return "OTHER";
    }

    private <T> PageResponse<T> paginate(List<T> items, Integer page, Integer size) {
        int safePage = Math.max(0, page == null ? 0 : page);
        int safeSize = Math.max(1, Math.min(100, size == null ? 10 : size));
        int start = Math.min(items.size(), safePage * safeSize);
        int end = Math.min(items.size(), start + safeSize);
        return new PageResponse<>(items.subList(start, end), items.size(), safePage, safeSize);
    }

    private String sessionTitle(Session session) {
        if (session == null) return "Interview session";
        List<String> topics = session.getTopics();
        if (topics != null && !topics.isEmpty()) return String.join(", ", topics);
        String interviewType = normalize(session.getInterviewType());
        if (interviewType != null) return interviewType;
        String title = normalize(session.getTitle());
        if (title != null) return title;
        return "Interview session";
    }

    private List<AdminDtos.PlatformHealthIndicator> buildHealthIndicators(long totalSessions,
                                                                          long openReports,
                                                                          long cancelledSessions,
                                                                          long pendingVerification,
                                                                          long flaggedReviews,
                                                                          double averageTrustScore) {
        List<AdminDtos.PlatformHealthIndicator> indicators = new ArrayList<>();
        double cancellationRate = totalSessions == 0 ? 0.0 : (cancelledSessions * 100.0) / totalSessions;
        String queueStatus = openReports > 15 ? "DEGRADED" : openReports > 7 ? "WATCH" : "HEALTHY";
        String cancellationStatus = cancellationRate > 30.0 ? "DEGRADED" : cancellationRate > 18.0 ? "WATCH" : "HEALTHY";
        String verificationStatus = pendingVerification > 20 ? "WATCH" : "HEALTHY";
        String trustStatus = averageTrustScore < 70.0 ? "DEGRADED" : averageTrustScore < 82.0 ? "WATCH" : "HEALTHY";

        indicators.add(new AdminDtos.PlatformHealthIndicator("MODERATION_QUEUE", queueStatus,
                String.format(Locale.ROOT, "%d open trust reports", openReports), Instant.now().toString()));
        indicators.add(new AdminDtos.PlatformHealthIndicator("SESSION_RELIABILITY", cancellationStatus,
                String.format(Locale.ROOT, "%.1f%% cancellation rate", cancellationRate), Instant.now().toString()));
        indicators.add(new AdminDtos.PlatformHealthIndicator("VERIFICATION_BACKLOG", verificationStatus,
                String.format(Locale.ROOT, "%d pending verification requests", pendingVerification), Instant.now().toString()));
        indicators.add(new AdminDtos.PlatformHealthIndicator("TRUST_SIGNAL", trustStatus,
                String.format(Locale.ROOT, "%.1f%% average trust score, %d flagged reviews", averageTrustScore, flaggedReviews), Instant.now().toString()));
        return indicators;
    }

    private Map<LocalDate, Long> bucketCounts(List<Instant> values) {
        return values.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(instant -> instant.atZone(ZoneOffset.UTC).toLocalDate(), Collectors.counting()));
    }

    private boolean matchesAuditQuery(ModerationAuditLog log, String query) {
        String value = query.toLowerCase(Locale.ROOT);
        return List.of(log.getAction(), log.getReason(), log.getSummary(), log.getEntityType(), log.getEntityId(), log.getActorUserId(), log.getSubjectUserId()).stream()
                .filter(Objects::nonNull)
                .map(item -> item.toLowerCase(Locale.ROOT))
                .anyMatch(item -> item.contains(value));
    }

    private AdminDtos.AuditLogPage toAuditPage(List<ModerationAuditLog> logs, int page, int size) {
        int start = Math.min(logs.size(), page * size);
        int end = Math.min(logs.size(), start + size);
        int totalPages = size <= 0 ? 0 : (int) Math.ceil((double) logs.size() / size);
        return new AdminDtos.AuditLogPage(
                logs.subList(start, end).stream().map(this::toAuditItem).toList(),
                page,
                size,
                logs.size(),
                totalPages
        );
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
            if (!VERIFICATION_STATUSES.contains(value)) {
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

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String userDisplayName(User user) {
        return user == null ? "" : user.getDisplayName();
    }

    private String toIso(Instant value) {
        return value == null ? null : value.toString();
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

    private Optional<Instant> parseSessionInstant(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(OffsetDateTime.parse(value).toInstant());
        } catch (DateTimeParseException firstEx) {
            try {
                return Optional.of(Instant.parse(value));
            } catch (DateTimeParseException secondEx) {
                return Optional.empty();
            }
        }
    }

    private record SessionRisk(boolean cancellation, boolean noShowRisk, boolean disputeRisk) {}
}
