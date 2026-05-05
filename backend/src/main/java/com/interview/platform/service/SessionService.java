package com.interview.platform.service;

import com.interview.platform.model.Session;
import com.interview.platform.model.User;
import com.interview.platform.repository.SessionRepository;
import com.interview.platform.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class SessionService {

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private UserRepository userRepository;

    public Session createSession(Session session) {

        // ── Set default status ────────────────────────────────────────────────
        if (session.getStatus() == null) {
            session.setStatus("SCHEDULED");
        }

        // ── Auto-match interviewer if none provided ───────────────────────────
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
            // If no interviewers exist at all, leave interviewerId null —
            // controller/caller can handle this scenario.
        }

        return sessionRepository.save(session);
    }

    public List<Session> getAllSessions() {
        return sessionRepository.findAll();
    }

    public Optional<Session> getById(String id) {
        return sessionRepository.findById(id);
    }
    public Session updateSessionStatus(String id, String status) {
        Optional<Session> sessionOpt = sessionRepository.findById(id);
        if (sessionOpt.isPresent()) {
            Session session = sessionOpt.get();
            session.setStatus(status);
            return sessionRepository.save(session);
        }
        return null;
    }
}

