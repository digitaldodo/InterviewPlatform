package com.interview.platform.service;

import com.interview.platform.model.Feedback;
import com.interview.platform.model.Session;
import com.interview.platform.model.User;
import com.interview.platform.model.UserReport;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class TrustSignalService {
    public UserTrustSnapshot evaluate(User user, List<Session> sessions, List<Feedback> feedback, List<UserReport> reports) {
        if (user == null) {
            return new UserTrustSnapshot(100.0, 100.0, 100.0, 100.0, 100.0, List.of());
        }
        List<Feedback> safeFeedback = feedback == null ? List.of() : feedback.stream().filter(Objects::nonNull).toList();
        List<UserReport> safeReports = reports == null ? List.of() : reports.stream().filter(Objects::nonNull).toList();
        int completed = user.getCompletedSessions() == null ? 0 : user.getCompletedSessions();
        int cancelled = user.getCancelledSessions() == null ? 0 : user.getCancelledSessions();
        int totalDecidedSessions = completed + cancelled;
        double sessionCompletionRate = totalDecidedSessions == 0 ? 100.0 : round((completed * 100.0) / totalDecidedSessions);
        double cancellationReliability = totalDecidedSessions == 0 ? 100.0 : round(100.0 - ((cancelled * 100.0) / totalDecidedSessions));

        List<Feedback> authoredReviews = safeFeedback.stream()
                .filter(item -> Objects.equals(user.getId(), item.getReviewerId()))
                .toList();
        List<Feedback> receivedPublicReviews = safeFeedback.stream()
                .filter(item -> Objects.equals(user.getId(), item.getInterviewerId()))
                .filter(item -> Boolean.TRUE.equals(item.getPublicReview()))
                .toList();
        List<UserReport> reportsAgainstUser = safeReports.stream()
                .filter(item -> Objects.equals(user.getId(), item.getReportedUserId()))
                .toList();

        double reviewQualityScore = authoredReviews.isEmpty()
                ? 100.0
                : round(authoredReviews.stream().mapToDouble(Feedback::getReviewQualityScore).average().orElse(0.0) * 100.0);

        double responseConsistencyScore = 100.0;
        if (receivedPublicReviews.size() >= 2) {
            double averageRating = receivedPublicReviews.stream().mapToInt(Feedback::getRating).average().orElse(0.0);
            double variance = receivedPublicReviews.stream()
                    .mapToDouble(item -> Math.pow(item.getRating() - averageRating, 2))
                    .average()
                    .orElse(0.0);
            responseConsistencyScore = round(Math.max(40.0, 100.0 - (variance * 18.0)));
        }

        double trustScore = round(
                sessionCompletionRate * 0.35
                        + cancellationReliability * 0.25
                        + responseConsistencyScore * 0.20
                        + reviewQualityScore * 0.20
                        - Math.min(18.0, reportsAgainstUser.size() * 4.0)
                        - Math.min(18.0, authoredReviews.stream().filter(item -> Boolean.TRUE.equals(item.getFlaggedForModeration())).count() * 6.0)
        );
        trustScore = Math.max(0.0, Math.min(100.0, trustScore));

        List<String> indicators = new ArrayList<>();
        if (sessionCompletionRate < 75.0) indicators.add("LOW_COMPLETION_RATE");
        if (cancellationReliability < 75.0) indicators.add("CANCELLATION_RISK");
        if (reviewQualityScore < 55.0) indicators.add("LOW_REVIEW_QUALITY");
        if (authoredReviews.stream().anyMatch(item -> Boolean.TRUE.equals(item.getFlaggedForModeration()))) indicators.add("FLAGGED_REVIEW_ACTIVITY");
        if (reportsAgainstUser.stream().anyMatch(item -> "OPEN".equalsIgnoreCase(item.getStatus()))) indicators.add("OPEN_REPORTS");
        if ("PENDING".equalsIgnoreCase(user.getVerificationRequestStatus())) indicators.add("VERIFICATION_PENDING");

        return new UserTrustSnapshot(
                trustScore,
                sessionCompletionRate,
                cancellationReliability,
                responseConsistencyScore,
                reviewQualityScore,
                indicators
        );
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    public record UserTrustSnapshot(
            double trustScore,
            double sessionCompletionRate,
            double cancellationReliability,
            double responseConsistencyScore,
            double reviewQualityScore,
            List<String> indicators
    ) {}
}
