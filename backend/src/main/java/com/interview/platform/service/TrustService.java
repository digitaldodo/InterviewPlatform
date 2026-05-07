package com.interview.platform.service;

import com.interview.platform.dto.ReportDtos;
import com.interview.platform.exception.ResourceNotFoundException;
import com.interview.platform.model.Session;
import com.interview.platform.model.User;
import com.interview.platform.model.UserReport;
import com.interview.platform.repository.SessionRepository;
import com.interview.platform.repository.UserReportRepository;
import com.interview.platform.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class TrustService {
    private final UserReportRepository userReportRepository;
    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;

    public TrustService(UserReportRepository userReportRepository,
                        UserRepository userRepository,
                        SessionRepository sessionRepository) {
        this.userReportRepository = userReportRepository;
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
    }

    public UserReport submitReport(User actor, ReportDtos.CreateReportRequest request) {
        if (actor == null || actor.getId() == null || actor.getId().isBlank()) {
            throw new IllegalArgumentException("Authentication required");
        }
        if (request == null || request.getReportedUserId() == null || request.getReportedUserId().isBlank()) {
            throw new IllegalArgumentException("Choose a user to report");
        }
        if (normalize(request.getReason()) == null) {
            throw new IllegalArgumentException("Report reason is required");
        }
        if (actor.getId().equals(request.getReportedUserId())) {
            throw new IllegalArgumentException("You cannot report yourself");
        }
        userRepository.findById(request.getReportedUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Reported user not found"));

        String sessionId = normalize(request.getSessionId());
        if (sessionId != null) {
            Session session = sessionRepository.findById(sessionId)
                    .orElseThrow(() -> new ResourceNotFoundException("Session not found"));
            boolean actorParticipant = actor.getId().equals(session.getCandidateId()) || actor.getId().equals(session.getInterviewerId());
            boolean targetParticipant = request.getReportedUserId().equals(session.getCandidateId()) || request.getReportedUserId().equals(session.getInterviewerId());
            if (!actorParticipant || !targetParticipant) {
                throw new IllegalArgumentException("Reports must reference a session you participated in");
            }
        }
        if (userReportRepository.existsByReporterIdAndReportedUserIdAndSessionId(actor.getId(), request.getReportedUserId(), sessionId)) {
            throw new IllegalArgumentException("You have already reported this user for the same session");
        }

        UserReport report = new UserReport();
        report.setReporterId(actor.getId());
        report.setReportedUserId(request.getReportedUserId());
        report.setSessionId(sessionId);
        report.setReason(normalize(request.getReason()));
        report.setDetails(normalize(request.getDetails()));
        report.setStatus("OPEN");
        report.setCreatedAt(Instant.now());
        report.setUpdatedAt(Instant.now());
        return userReportRepository.save(report);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
