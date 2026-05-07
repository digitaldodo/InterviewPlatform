package com.interview.platform.service;

import com.interview.platform.exception.ResourceNotFoundException;
import com.interview.platform.exception.UnauthorizedException;
import com.interview.platform.model.Feedback;
import com.interview.platform.model.InterviewReport;
import com.interview.platform.model.Session;
import com.interview.platform.model.User;
import com.interview.platform.repository.FeedbackRepository;
import com.interview.platform.repository.InterviewReportRepository;
import com.interview.platform.repository.SessionRepository;
import com.interview.platform.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class InterviewReportService {
    private final InterviewReportRepository reportRepository;
    private final SessionRepository sessionRepository;
    private final FeedbackRepository feedbackRepository;
    private final UserRepository userRepository;

    public InterviewReportService(InterviewReportRepository reportRepository,
                                 SessionRepository sessionRepository,
                                 FeedbackRepository feedbackRepository,
                                 UserRepository userRepository) {
        this.reportRepository = reportRepository;
        this.sessionRepository = sessionRepository;
        this.feedbackRepository = feedbackRepository;
        this.userRepository = userRepository;
    }

    public InterviewReport getForSession(String sessionId, User actor) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));
        requireParticipant(session, actor);
        Optional<InterviewReport> existing = reportRepository.findBySessionId(sessionId);
        if (existing.isPresent()) {
            return existing.get();
        }
        Feedback latest = feedbackRepository.findBySessionId(sessionId).stream()
                .filter(item -> session.getInterviewerId() != null && session.getInterviewerId().equals(item.getReviewerId()))
                .max(Comparator.comparing(item -> item.getCreatedAt() == null ? Instant.EPOCH : item.getCreatedAt()))
                .orElseThrow(() -> new ResourceNotFoundException("Report not available yet"));
        return buildFromFeedback(session, latest).orElseThrow(() -> new ResourceNotFoundException("Report not available yet"));
    }

    public InterviewReport upsertFromFeedback(Feedback feedback) {
        if (feedback == null || feedback.getSessionId() == null || feedback.getSessionId().isBlank()) {
            throw new IllegalArgumentException("Session ID is required");
        }
        Session session = sessionRepository.findById(feedback.getSessionId())
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));
        if (session.getInterviewerId() == null || !session.getInterviewerId().equals(feedback.getReviewerId())) {
            throw new UnauthorizedException("Only the interviewer can publish a report for this session");
        }
        InterviewReport report = reportRepository.findBySessionId(session.getId()).orElseGet(InterviewReport::new);
        if (report.getCreatedAt() == null) {
            report.setCreatedAt(Instant.now());
        }
        report.setUpdatedAt(Instant.now());
        report.setSessionId(session.getId());
        report.setInterviewerId(session.getInterviewerId());
        report.setIntervieweeId(session.getCandidateId());
        report.setSessionStartTime(session.getStartTime());
        report.setTopics(session.getTopics());

        User interviewer = userRepository.findById(session.getInterviewerId()).orElse(null);
        User interviewee = userRepository.findById(session.getCandidateId()).orElse(null);
        report.setInterviewerName(interviewer == null ? "Interviewer" : interviewer.getName());
        report.setIntervieweeName(interviewee == null ? "Interviewee" : interviewee.getName());

        report.setOverallRating(feedback.getRating());
        report.setStrengths(trimToNull(feedback.getStrengths()));
        report.setWeaknesses(trimToNull(feedback.getWeaknesses()));
        report.setImprovementRoadmap(trimToNull(firstNonBlank(feedback.getImprovementAreas(), feedback.getRecommendations())));
        report.setInterviewerComments(trimToNull(feedback.getComments()));
        report.setTopicReports(mapTopicReports(feedback.getTopicFeedback()));

        return reportRepository.save(report);
    }

    private Optional<InterviewReport> buildFromFeedback(Session session, Feedback feedback) {
        if (session == null || feedback == null) return Optional.empty();
        InterviewReport report = new InterviewReport();
        report.setCreatedAt(feedback.getCreatedAt() == null ? Instant.now() : feedback.getCreatedAt());
        report.setUpdatedAt(Instant.now());
        report.setSessionId(session.getId());
        report.setInterviewerId(session.getInterviewerId());
        report.setIntervieweeId(session.getCandidateId());
        report.setSessionStartTime(session.getStartTime());
        report.setTopics(session.getTopics());
        User interviewer = userRepository.findById(session.getInterviewerId()).orElse(null);
        User interviewee = userRepository.findById(session.getCandidateId()).orElse(null);
        report.setInterviewerName(interviewer == null ? "Interviewer" : interviewer.getName());
        report.setIntervieweeName(interviewee == null ? "Interviewee" : interviewee.getName());
        report.setOverallRating(feedback.getRating());
        report.setStrengths(trimToNull(feedback.getStrengths()));
        report.setWeaknesses(trimToNull(feedback.getWeaknesses()));
        report.setImprovementRoadmap(trimToNull(firstNonBlank(feedback.getImprovementAreas(), feedback.getRecommendations())));
        report.setInterviewerComments(trimToNull(feedback.getComments()));
        report.setTopicReports(mapTopicReports(feedback.getTopicFeedback()));
        return Optional.of(report);
    }

    private List<InterviewReport.TopicReport> mapTopicReports(List<Feedback.TopicFeedback> topics) {
        if (topics == null) return List.of();
        return topics.stream().map(item -> {
            InterviewReport.TopicReport report = new InterviewReport.TopicReport();
            report.setTopic(trimToNull(item.getTopic()));
            report.setRating(item.getRating());
            report.setStrengths(trimToNull(item.getStrengths()));
            report.setWeaknesses(trimToNull(item.getWeaknesses()));
            report.setImprovementAreas(trimToNull(item.getImprovementAreas()));
            report.setComments(trimToNull(item.getComments()));
            report.setSkillRatings(item.getSkillRatings());
            return report;
        }).toList();
    }

    private void requireParticipant(Session session, User actor) {
        if (actor == null || actor.getId() == null || actor.getId().isBlank()) {
            throw new UnauthorizedException("Authentication required");
        }
        boolean participant = actor.getId().equals(session.getInterviewerId()) || actor.getId().equals(session.getCandidateId());
        if (!participant) {
            throw new UnauthorizedException("You are not allowed to access this report");
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) return first;
        return second;
    }
}
