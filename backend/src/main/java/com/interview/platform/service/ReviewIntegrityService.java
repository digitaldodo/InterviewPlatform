package com.interview.platform.service;

import com.interview.platform.model.Feedback;
import com.interview.platform.model.Session;
import com.interview.platform.model.User;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class ReviewIntegrityService {
    private static final int MAX_REVIEWS_PER_DAY = 5;
    private static final Pattern URL_PATTERN = Pattern.compile("(https?://|www\\.)", Pattern.CASE_INSENSITIVE);
    private static final Pattern REPEATED_CHAR_PATTERN = Pattern.compile("(.)\\1{6,}");

    public ReviewIntegrityResult analyze(User actor,
                                         Session session,
                                         Feedback feedback,
                                         List<Feedback> recentFeedback) {
        validateSessionParticipants(actor, session);
        String comments = normalizeComment(feedback.getComments());
        ensureReviewHasSignal(comments);

        Instant now = Instant.now();
        List<Feedback> recent = recentFeedback == null ? List.of() : recentFeedback;
        long reviewsLastDay = recent.stream()
                .filter(item -> item.getCreatedAt() != null && item.getCreatedAt().isAfter(now.minus(Duration.ofHours(24))))
                .count();
        if (reviewsLastDay >= MAX_REVIEWS_PER_DAY) {
            throw new IllegalArgumentException("Review rate limit reached. Please wait before submitting more reviews.");
        }

        String fingerprint = fingerprint(comments);
        Set<String> flags = new LinkedHashSet<>();
        int suspiciousScore = 0;
        double qualityScore = calculateQualityScore(comments, feedback);

        if (isSpamLike(comments)) {
            flags.add("SPAM_PATTERN");
            suspiciousScore += 40;
        }
        if (recent.stream().anyMatch(item -> fingerprint.equals(item.getContentFingerprint()))) {
            flags.add("DUPLICATE_CONTENT");
            suspiciousScore += 45;
        }
        long burstCount = recent.stream()
                .filter(item -> item.getCreatedAt() != null && item.getCreatedAt().isAfter(now.minus(Duration.ofMinutes(15))))
                .count();
        if (burstCount >= 2) {
            flags.add("RAPID_REVIEW_BURST");
            suspiciousScore += 25;
        }
        long lowQualityRecent = recent.stream()
                .filter(item -> item.getReviewQualityScore() < 0.35)
                .count();
        if (qualityScore < 0.35 && lowQualityRecent >= 2) {
            flags.add("REPEATED_LOW_QUALITY");
            suspiciousScore += 25;
        }
        if (hasSuspiciousRatingPattern(feedback, recent)) {
            flags.add("SUSPICIOUS_RATING_PATTERN");
            suspiciousScore += 20;
        }

        boolean flagged = suspiciousScore >= 40 || flags.contains("SPAM_PATTERN") || flags.contains("DUPLICATE_CONTENT");
        return new ReviewIntegrityResult(
                comments,
                fingerprint,
                new ArrayList<>(flags),
                suspiciousScore,
                qualityScore,
                flagged,
                now.minus(Duration.ofHours(24))
        );
    }

    private void validateSessionParticipants(User actor, Session session) {
        if (session == null || isBlank(session.getCandidateId()) || isBlank(session.getInterviewerId())) {
            throw new IllegalArgumentException("Malformed session cannot accept reviews");
        }
        if (session.getCandidateId().equals(session.getInterviewerId())) {
            throw new IllegalArgumentException("Malformed session cannot accept reviews");
        }
        boolean candidateReviewer = actor.getId().equals(session.getCandidateId());
        boolean interviewerReviewer = actor.getId().equals(session.getInterviewerId());
        if (!candidateReviewer && !interviewerReviewer) {
            throw new IllegalArgumentException("Only session participants can submit feedback");
        }
        String targetUserId = candidateReviewer ? session.getInterviewerId() : session.getCandidateId();
        if (actor.getId().equals(targetUserId)) {
            throw new IllegalArgumentException("Self-review is not allowed");
        }
    }

    private void ensureReviewHasSignal(String comments) {
        if (comments.length() < 16 || wordCount(comments) < 3) {
            throw new IllegalArgumentException("Review comments must be meaningful and specific");
        }
    }

    private boolean hasSuspiciousRatingPattern(Feedback feedback, List<Feedback> recent) {
        if (recent.size() < 3) {
            return false;
        }
        int rating = feedback.getRating();
        if (rating != 1 && rating != 5) {
            return false;
        }
        long sameExtreme = recent.stream()
                .limit(4)
                .filter(item -> item.getRating() == rating)
                .count();
        return sameExtreme >= 3;
    }

    private boolean isSpamLike(String comments) {
        String normalized = comments.toLowerCase(Locale.ROOT);
        int uniqueWordCount = (int) List.of(normalized.split("\\s+")).stream().distinct().count();
        return URL_PATTERN.matcher(normalized).find()
                || REPEATED_CHAR_PATTERN.matcher(normalized).find()
                || uniqueWordCount <= 2
                || normalized.matches("^[\\W_]+$");
    }

    private double calculateQualityScore(String comments, Feedback feedback) {
        int wordCount = wordCount(comments);
        double wordScore = Math.min(1.0, wordCount / 20.0);
        double structureScore = feedback.getTopicFeedback().isEmpty() ? 0.15 : 0.35;
        double fieldScore = 0.0;
        if (!isBlank(feedback.getStrengths())) fieldScore += 0.15;
        if (!isBlank(feedback.getWeaknesses()) || !isBlank(feedback.getImprovementAreas())) fieldScore += 0.15;
        if (!isBlank(feedback.getRecommendations())) fieldScore += 0.1;
        return Math.round(Math.min(1.0, wordScore * 0.55 + structureScore + fieldScore) * 100.0) / 100.0;
    }

    private String normalizeComment(String comment) {
        if (comment == null) {
            return "";
        }
        return comment.trim().replaceAll("\\s+", " ");
    }

    private int wordCount(String text) {
        if (isBlank(text)) {
            return 0;
        }
        return (int) List.of(text.trim().split("\\s+")).stream()
                .filter(value -> !value.isBlank())
                .count();
    }

    private String fingerprint(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Unable to hash review content", ex);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record ReviewIntegrityResult(
            String normalizedComments,
            String contentFingerprint,
            List<String> suspiciousFlags,
            int suspiciousScore,
            double reviewQualityScore,
            boolean flaggedForModeration,
            Instant rateLimitWindowStart
    ) {}
}
