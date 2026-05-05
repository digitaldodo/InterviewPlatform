package com.interview.platform.service;

import com.interview.platform.model.Session;
import com.interview.platform.model.User;
import com.interview.platform.exception.ResourceNotFoundException;
import com.interview.platform.repository.SessionRepository;
import com.interview.platform.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class SessionService {

    private static final Set<String> VALID_STATUSES = new HashSet<>(Arrays.asList(
            "PENDING", "CONFIRMED", "COMPLETED", "CANCELLED"
    ));

    private final SessionRepository sessionRepository;
    private final UserRepository userRepository;

    public SessionService(SessionRepository sessionRepository, UserRepository userRepository) {
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
    }

    public Session createSession(Session session) {
        if (session == null) {
            throw new IllegalArgumentException("Session details are required");
        }
        if (session.getCandidateId() == null || session.getCandidateId().isBlank()) {
            throw new IllegalArgumentException("Interviewee ID is required");
        }
        if (session.getTitle() == null || session.getTitle().isBlank()) {
            throw new IllegalArgumentException("Session topic is required");
        }
        session.setTitle(session.getTitle().trim());

        if (session.getStatus() == null) {
            session.setStatus("PENDING");
        } else {
            session.setStatus(normalizeStatus(session.getStatus()));
        }

        if (session.getInterviewerId() == null || session.getInterviewerId().isBlank()) {

            List<User> interviewers = userRepository.findByRole("INTERVIEWER");

            if (!interviewers.isEmpty()) {
                // Preferred: find an interviewer whose skills overlap with the session title
                String title = (session.getTitle() != null) ? session.getTitle().toLowerCase() : "";

                Optional<User> skillMatch = interviewers.stream()
                        .filter(u -> u.getSkills() != null &&
                                     u.getSkills().stream()
                                             .anyMatch(skill -> title.contains(skill.toLowerCase())))
                        .findFirst();

                // Use skill-matched interviewer, or fall back to first available
                User assigned = skillMatch.orElse(interviewers.get(0));
                session.setInterviewerId(assigned.getId());
            }
        } else if (!userRepository.existsById(session.getInterviewerId())) {
            throw new IllegalArgumentException("Interviewer does not exist");
        }

        if (!userRepository.existsById(session.getCandidateId())) {
            throw new IllegalArgumentException("Interviewee does not exist");
        }

        return sessionRepository.save(session);
    }

    public List<Session> getAllSessions() {
        return sessionRepository.findAll();
    }

    public Optional<Session> getById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return sessionRepository.findById(id);
    }

    public List<Session> getByInterviewerId(String interviewerId) {
        return sessionRepository.findByInterviewerId(interviewerId);
    }

    public List<Session> getByIntervieweeId(String intervieweeId) {
        return sessionRepository.findByCandidateId(intervieweeId);
    }

    public Session updateSessionStatus(String id, String status) {
        Session session = sessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));
        session.setStatus(normalizeStatus(status));
        return sessionRepository.save(session);
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("Status is required");
        }
        String normalized = status.trim().toUpperCase();
        if (!VALID_STATUSES.contains(normalized)) {
            throw new IllegalArgumentException("Status must be PENDING, CONFIRMED, COMPLETED, or CANCELLED");
        }
        return normalized;
    }
}

