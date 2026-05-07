package com.interview.platform.service;

import com.interview.platform.dto.ReportDtos;
import com.interview.platform.dto.UserDtos;
import com.interview.platform.exception.ResourceNotFoundException;
import com.interview.platform.model.Session;
import com.interview.platform.model.User;
import com.interview.platform.model.UserReport;
import com.interview.platform.repository.SessionRepository;
import com.interview.platform.repository.UserReportRepository;
import com.interview.platform.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class TrustService {
    private static final Set<String> VALID_REPORT_CATEGORIES = Set.of("SAFETY", "NO_SHOW", "QUALITY", "SPAM", "PROFILE", "OTHER");
    private static final Set<String> PUBLIC_EMAIL_DOMAINS = Set.of(
            "gmail.com", "yahoo.com", "hotmail.com", "outlook.com", "icloud.com", "protonmail.com", "aol.com"
    );

    private final UserReportRepository userReportRepository;
    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final ModerationAuditService moderationAuditService;

    public TrustService(UserReportRepository userReportRepository,
                        UserRepository userRepository,
                        SessionRepository sessionRepository,
                        ModerationAuditService moderationAuditService) {
        this.userReportRepository = userReportRepository;
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.moderationAuditService = moderationAuditService;
    }

    public UserReport submitReport(User actor, ReportDtos.CreateReportRequest request) {
        if (actor == null || actor.getId() == null || actor.getId().isBlank()) {
            throw new IllegalArgumentException("Authentication required");
        }
        if (request == null || request.getReportedUserId() == null || request.getReportedUserId().isBlank()) {
            throw new IllegalArgumentException("Choose a user to report");
        }
        String details = normalize(request.getDetails());
        if (details == null || details.length() < 12) {
            throw new IllegalArgumentException("Report details must be at least 12 characters");
        }
        if (actor.getId().equals(request.getReportedUserId())) {
            throw new IllegalArgumentException("You cannot report yourself");
        }
        userRepository.findById(request.getReportedUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Reported user not found"));

        String category = normalizeCategory(request.getCategory(), request.getReason());
        String sessionId = normalize(request.getSessionId());
        if (sessionId != null) {
            Session session = sessionRepository.findById(sessionId)
                    .orElseThrow(() -> new ResourceNotFoundException("Session not found"));
            if (session.getCandidateId() == null || session.getInterviewerId() == null || session.getCandidateId().equals(session.getInterviewerId())) {
                throw new IllegalArgumentException("Reports must reference a valid session");
            }
            boolean actorParticipant = actor.getId().equals(session.getCandidateId()) || actor.getId().equals(session.getInterviewerId());
            boolean targetParticipant = request.getReportedUserId().equals(session.getCandidateId()) || request.getReportedUserId().equals(session.getInterviewerId());
            if (!actorParticipant || !targetParticipant) {
                throw new IllegalArgumentException("Reports must reference a session you participated in");
            }
        }

        List<UserReport> recentReports = userReportRepository.findByReporterIdAndReportedUserIdOrderByCreatedAtDesc(actor.getId(), request.getReportedUserId());
        boolean duplicate = recentReports.stream().anyMatch(report ->
                sameSession(report.getSessionId(), sessionId)
                        && category.equalsIgnoreCase(normalize(report.getCategory()))
        );
        boolean recentProfileDuplicate = sessionId == null && recentReports.stream().anyMatch(report ->
                sameSession(report.getSessionId(), null)
                        && category.equalsIgnoreCase(normalize(report.getCategory()))
                        && report.getCreatedAt() != null
                        && report.getCreatedAt().isAfter(Instant.now().minus(7, ChronoUnit.DAYS))
        );
        if (duplicate || recentProfileDuplicate || userReportRepository.existsByReporterIdAndReportedUserIdAndSessionId(actor.getId(), request.getReportedUserId(), sessionId)) {
            throw new IllegalArgumentException("A similar report has already been submitted");
        }

        int relatedCount = (int) userReportRepository.findTop20ByReportedUserIdOrderByCreatedAtDesc(request.getReportedUserId()).stream()
                .filter(report -> category.equalsIgnoreCase(normalize(report.getCategory())))
                .filter(report -> sameSession(report.getSessionId(), sessionId))
                .count();

        UserReport report = new UserReport();
        report.setReporterId(actor.getId());
        report.setReportedUserId(request.getReportedUserId());
        report.setSessionId(sessionId);
        report.setCategory(category);
        report.setReason(normalize(request.getReason()) == null ? category : normalize(request.getReason()));
        report.setDetails(details);
        report.setStatus("OPEN");
        report.setDuplicateCount(Math.max(0, relatedCount));
        report.setCreatedAt(Instant.now());
        report.setUpdatedAt(Instant.now());
        return userReportRepository.save(report);
    }

    public User submitVerificationRequest(User actor, UserDtos.VerificationRequestSubmission request) {
        if (actor == null || actor.getId() == null || actor.getId().isBlank()) {
            throw new IllegalArgumentException("Authentication required");
        }
        User user = userRepository.findById(actor.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (!user.hasRole("INTERVIEWER")) {
            throw new IllegalArgumentException("Only interviewer accounts can request verification");
        }
        String linkedInUrl = normalize(request == null ? null : request.getLinkedInUrl());
        String companyEmail = normalize(request == null ? null : request.getCompanyEmail());
        String notes = normalize(request == null ? null : request.getNotes());

        if (linkedInUrl == null && companyEmail == null && notes == null) {
            throw new IllegalArgumentException("Add at least one verification detail before submitting");
        }
        if (linkedInUrl != null && !(linkedInUrl.startsWith("http://") || linkedInUrl.startsWith("https://"))) {
            throw new IllegalArgumentException("LinkedIn or profile URL must start with http:// or https://");
        }
        if (companyEmail != null && !looksLikeCompanyEmail(companyEmail)) {
            throw new IllegalArgumentException("Use a company email address rather than a public email provider");
        }

        user.setLinkedInUrl(linkedInUrl != null ? linkedInUrl : user.getLinkedInUrl());
        user.setVerificationCompanyEmail(companyEmail != null ? companyEmail : user.getVerificationCompanyEmail());
        user.setVerificationRequestNotes(notes);
        user.setVerificationRequestStatus("PENDING");
        user.setVerificationRequestedAt(Instant.now());
        user.setVerificationReviewedAt(null);
        user.setVerificationApprovedAt(null);
        user.setInterviewerVerified(false);
        User saved = userRepository.save(user);
        moderationAuditService.log(
                "VERIFICATION",
                saved.getId(),
                saved.getId(),
                saved.getId(),
                "VERIFICATION_REQUESTED",
                "User requested interviewer verification",
                "Verification request submitted",
                null,
                auditState(saved)
        );
        return saved;
    }

    private Map<String, Object> auditState(User user) {
        Map<String, Object> state = new HashMap<>();
        state.put("status", user.getVerificationRequestStatus());
        state.put("linkedInUrl", user.getLinkedInUrl());
        state.put("companyEmail", user.getVerificationCompanyEmail());
        return state;
    }

    private boolean looksLikeCompanyEmail(String email) {
        if (email == null || !email.contains("@")) {
            return false;
        }
        String domain = email.substring(email.indexOf('@') + 1).toLowerCase(Locale.ROOT);
        return !PUBLIC_EMAIL_DOMAINS.contains(domain);
    }

    private boolean sameSession(String left, String right) {
        String normalizedLeft = normalize(left);
        String normalizedRight = normalize(right);
        if (normalizedLeft == null || normalizedRight == null) {
            return normalizedLeft == null && normalizedRight == null;
        }
        return normalizedLeft.equals(normalizedRight);
    }

    private String normalizeCategory(String category, String reason) {
        String normalized = normalize(category);
        if (normalized == null) {
            normalized = normalize(reason);
        }
        if (normalized == null) {
            throw new IllegalArgumentException("Report category is required");
        }
        normalized = normalized.toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if (normalized.contains("SCAM")) normalized = "SPAM";
        if (normalized.contains("NO_SHOW") || normalized.contains("LATE")) normalized = "NO_SHOW";
        if (normalized.contains("HARASS") || normalized.contains("ABUSE")) normalized = "SAFETY";
        if (normalized.contains("FAKE") || normalized.contains("IMPERSON")) normalized = "PROFILE";
        if (normalized.contains("UNPROFESSIONAL")) normalized = "QUALITY";
        if (!VALID_REPORT_CATEGORIES.contains(normalized)) {
            normalized = "OTHER";
        }
        return normalized;
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
