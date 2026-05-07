package com.interview.platform.service;

import com.interview.platform.dto.AnalyticsDtos;
import com.interview.platform.exception.UnauthorizedException;
import com.interview.platform.model.Feedback;
import com.interview.platform.model.InterviewReport;
import com.interview.platform.model.Session;
import com.interview.platform.model.User;
import com.interview.platform.repository.FeedbackRepository;
import com.interview.platform.repository.InterviewReportRepository;
import com.interview.platform.repository.SessionRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class AnalyticsService {
    private final SessionRepository sessionRepository;
    private final InterviewReportRepository interviewReportRepository;
    private final FeedbackRepository feedbackRepository;

    public AnalyticsService(SessionRepository sessionRepository,
                            InterviewReportRepository interviewReportRepository,
                            FeedbackRepository feedbackRepository) {
        this.sessionRepository = sessionRepository;
        this.interviewReportRepository = interviewReportRepository;
        this.feedbackRepository = feedbackRepository;
    }

    public AnalyticsDtos.SummaryResponse summary(User actor, String workspace) {
        if (actor == null || actor.getId() == null || actor.getId().isBlank()) {
            throw new UnauthorizedException("Authentication required");
        }
        String mode = workspace == null ? "" : workspace.trim().toUpperCase();
        boolean interviewerMode = "INTERVIEWER".equals(mode);

        List<Session> sessions = sessionRepository.findByInterviewerIdOrCandidateId(actor.getId(), actor.getId());
        Instant now = Instant.now();
        int upcoming = (int) sessions.stream().filter(session -> isUpcoming(session, now)).count();
        int completed = (int) sessions.stream().filter(session -> "COMPLETED".equalsIgnoreCase(session.getStatus())).count();

        Double avgRating = interviewerMode
                ? averageInterviewerRating(actor.getId(), sessions)
                : averageIntervieweeRating(actor.getId(), sessions);

        Integer streak = streakDays(actor.getId(), sessions);
        List<AnalyticsDtos.TopicTrend> topicTrends = interviewerMode
                ? topicTrendsForInterviewer(actor.getId(), sessions)
                : topicTrendsForInterviewee(actor.getId());

        return new AnalyticsDtos.SummaryResponse(upcoming, completed, avgRating, streak, topicTrends);
    }

    private boolean isUpcoming(Session session, Instant now) {
        if (session == null) return false;
        String status = session.getStatus() == null ? "" : session.getStatus().trim().toUpperCase();
        if (!status.equals("PENDING") && !status.equals("CONFIRMED")) return false;
        Optional<Instant> time = parseSessionInstant(session.getStartTime());
        return time.map(instant -> instant.isAfter(now)).orElse(false);
    }

    private Optional<Instant> parseSessionInstant(String startTime) {
        if (startTime == null || startTime.isBlank()) return Optional.empty();
        try {
            return Optional.of(OffsetDateTime.parse(startTime).toInstant());
        } catch (DateTimeParseException ignored) {
            try {
                return Optional.of(Instant.parse(startTime));
            } catch (DateTimeParseException secondIgnored) {
                return Optional.empty();
            }
        }
    }

    private Integer streakDays(String userId, List<Session> sessions) {
        List<LocalDate> days = sessions.stream()
                .filter(session -> "COMPLETED".equalsIgnoreCase(session.getStatus()))
                .map(Session::getStartTime)
                .map(this::parseSessionInstant)
                .flatMap(Optional::stream)
                .map(instant -> LocalDate.ofInstant(instant, ZoneOffset.UTC))
                .distinct()
                .sorted(Comparator.reverseOrder())
                .toList();
        if (days.isEmpty()) return null;
        int streak = 1;
        for (int i = 1; i < days.size(); i += 1) {
            if (days.get(i - 1).minusDays(1).equals(days.get(i))) {
                streak += 1;
            } else {
                break;
            }
        }
        return streak;
    }

    private Double averageIntervieweeRating(String intervieweeId, List<Session> sessions) {
        List<String> completedSessionIds = sessions.stream()
                .filter(session -> "COMPLETED".equalsIgnoreCase(session.getStatus()))
                .map(Session::getId)
                .filter(id -> id != null && !id.isBlank())
                .toList();
        if (completedSessionIds.isEmpty()) return null;
        List<InterviewReport> reports = interviewReportRepository.findByIntervieweeIdOrderByCreatedAtDesc(intervieweeId).stream()
                .filter(report -> report.getSessionId() != null && completedSessionIds.contains(report.getSessionId()))
                .toList();
        if (reports.isEmpty()) return null;
        double avg = reports.stream()
                .mapToInt(report -> report.getOverallRating() == null ? 0 : report.getOverallRating())
                .filter(value -> value > 0)
                .average()
                .orElse(0.0);
        return avg == 0.0 ? null : Math.round(avg * 10.0) / 10.0;
    }

    private Double averageInterviewerRating(String interviewerId, List<Session> sessions) {
        List<String> sessionIds = sessions.stream()
                .map(Session::getId)
                .filter(id -> id != null && !id.isBlank())
                .toList();
        if (sessionIds.isEmpty()) return null;
        List<Feedback> feedback = feedbackRepository.findBySessionIdIn(sessionIds).stream()
                .filter(item -> item.getReviewerId() != null && !item.getReviewerId().equals(interviewerId))
                .toList();
        if (feedback.isEmpty()) return null;
        double avg = feedback.stream().mapToInt(Feedback::getRating).filter(value -> value > 0).average().orElse(0.0);
        return avg == 0.0 ? null : Math.round(avg * 10.0) / 10.0;
    }

    private List<AnalyticsDtos.TopicTrend> topicTrendsForInterviewee(String intervieweeId) {
        List<InterviewReport> reports = interviewReportRepository.findByIntervieweeIdOrderByCreatedAtDesc(intervieweeId);
        if (reports.isEmpty()) return List.of();
        Map<String, List<Integer>> ratings = new HashMap<>();
        for (InterviewReport report : reports) {
            for (InterviewReport.TopicReport topic : report.getTopicReports()) {
                if (topic.getTopic() == null || topic.getTopic().isBlank()) continue;
                int rating = topic.getRating() == null ? 0 : topic.getRating();
                if (rating <= 0) continue;
                ratings.computeIfAbsent(topic.getTopic(), ignored -> new ArrayList<>()).add(rating);
            }
        }
        return ratings.entrySet().stream()
                .map(entry -> new AnalyticsDtos.TopicTrend(
                        entry.getKey(),
                        Math.round(entry.getValue().stream().mapToInt(Integer::intValue).average().orElse(0.0) * 10.0) / 10.0,
                        entry.getValue().size()
                ))
                .sorted(Comparator.comparing((AnalyticsDtos.TopicTrend t) -> t.getAverageRating() == null ? 0.0 : t.getAverageRating()).reversed())
                .limit(8)
                .toList();
    }

    private List<AnalyticsDtos.TopicTrend> topicTrendsForInterviewer(String interviewerId, List<Session> sessions) {
        // For interviewers, show ratings by topic from interviewee reviews (Feedback.rating) grouped by session topics.
        List<String> sessionIds = sessions.stream().map(Session::getId).filter(id -> id != null && !id.isBlank()).toList();
        if (sessionIds.isEmpty()) return List.of();
        List<Feedback> feedback = feedbackRepository.findBySessionIdIn(sessionIds).stream()
                .filter(item -> item.getReviewerId() != null && !item.getReviewerId().equals(interviewerId))
                .toList();
        if (feedback.isEmpty()) return List.of();
        Map<String, Session> sessionById = new HashMap<>();
        for (Session session : sessions) {
            if (session != null && session.getId() != null) {
                sessionById.put(session.getId(), session);
            }
        }
        Map<String, List<Integer>> ratings = new HashMap<>();
        for (Feedback item : feedback) {
            Session session = sessionById.get(item.getSessionId());
            if (session == null) continue;
            List<String> topics = session.getTopics();
            for (String topic : topics) {
                if (topic == null || topic.isBlank()) continue;
                int rating = item.getRating();
                if (rating <= 0) continue;
                ratings.computeIfAbsent(topic, ignored -> new ArrayList<>()).add(rating);
            }
        }
        return ratings.entrySet().stream()
                .map(entry -> new AnalyticsDtos.TopicTrend(
                        entry.getKey(),
                        Math.round(entry.getValue().stream().mapToInt(Integer::intValue).average().orElse(0.0) * 10.0) / 10.0,
                        entry.getValue().size()
                ))
                .sorted(Comparator.comparing((AnalyticsDtos.TopicTrend t) -> t.getAverageRating() == null ? 0.0 : t.getAverageRating()).reversed())
                .limit(8)
                .toList();
    }
}
