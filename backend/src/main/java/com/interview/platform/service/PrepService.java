package com.interview.platform.service;

import com.interview.platform.dto.MarketplaceDtos;
import com.interview.platform.exception.ResourceNotFoundException;
import com.interview.platform.model.Feedback;
import com.interview.platform.model.PrepModule;
import com.interview.platform.model.Session;
import com.interview.platform.model.User;
import com.interview.platform.repository.FeedbackRepository;
import com.interview.platform.repository.PrepModuleRepository;
import com.interview.platform.repository.SessionRepository;
import com.interview.platform.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class PrepService {
    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final FeedbackRepository feedbackRepository;
    private final PrepModuleRepository prepModuleRepository;

    public PrepService(UserRepository userRepository,
                       SessionRepository sessionRepository,
                       FeedbackRepository feedbackRepository,
                       PrepModuleRepository prepModuleRepository) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.feedbackRepository = feedbackRepository;
        this.prepModuleRepository = prepModuleRepository;
    }

    public MarketplaceDtos.PrepHubResponse buildHub(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        List<Session> sessions = sessionRepository.findByCandidateId(userId);
        List<String> sessionIds = sessions.stream().map(Session::getId).filter(Objects::nonNull).toList();
        List<Feedback> feedback = sessionIds.isEmpty() ? List.of() : feedbackRepository.findBySessionIdIn(sessionIds);

        List<String> primaryTopics = orderedKeys(buildTopicSignal(user, sessions, feedback), 8);
        List<String> targetCompanies = targetCompanies(user, sessions);
        List<MarketplaceDtos.PrepModuleCard> modules = prepModuleRepository.findByVisibilityStatusOrderByUpdatedAtDesc("PUBLISHED")
                .stream()
                .map(this::toModuleCard)
                .toList();

        return new MarketplaceDtos.PrepHubResponse(
                personaLabel(user),
                primaryTopics,
                targetCompanies,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                modules
        );
    }

    private MarketplaceDtos.PrepModuleCard toModuleCard(PrepModule module) {
        return new MarketplaceDtos.PrepModuleCard(
                module.getId(),
                fallback(module.getTitle(), "Untitled module"),
                fallback(module.getDescription(), ""),
                fallback(module.getCategory(), "Uncategorized"),
                fallback(module.getDifficulty(), "Foundational"),
                sanitizeList(module.getTags()),
                module.getEstimatedDurationMinutes(),
                module.getVisibilityStatus(),
                module.getResources().stream()
                        .filter(Objects::nonNull)
                        .filter(item -> !normalize(item.getUrl()).isBlank())
                        .map(item -> new MarketplaceDtos.PrepModuleResource(
                                normalize(item.getLabel()).isBlank() ? normalize(item.getUrl()) : normalize(item.getLabel()),
                                normalize(item.getUrl())
                        ))
                        .toList(),
                module.getUpdatedAt() == null ? null : module.getUpdatedAt().toString(),
                module.getPublishedAt() == null ? null : module.getPublishedAt().toString()
        );
    }

    private String fallback(String value, String fallback) {
        String normalized = normalize(value);
        return normalized.isBlank() ? fallback : normalized;
    }

    private List<String> sanitizeList(List<String> values) {
        if (values == null) return List.of();
        return values.stream()
                .map(this::normalize)
                .filter(item -> !item.isBlank())
                .distinct()
                .limit(12)
                .toList();
    }

    private Map<String, Integer> buildTopicSignal(User user, List<Session> sessions, List<Feedback> feedback) {
        Map<String, Integer> signal = new LinkedHashMap<>();
        addSignal(signal, splitOptions(user.getInterviewTopics()), 5);
        addSignal(signal, splitOptions(user.getPreferredDomains()), 4);
        addSignal(signal, splitOptions(user.getSkills()), 3);
        sessions.forEach(session -> addSignal(signal, splitOptions(session.getTopics()), isStatus(session, "COMPLETED") ? 4 : 2));
        feedback.forEach(item -> item.getTopicFeedback().forEach(topic -> {
            String normalized = normalize(topic.getTopic());
            if (normalized.isBlank()) return;
            int rating = topic.getRating() == null ? 0 : topic.getRating();
            if (rating > 0 && rating <= 3) {
                signal.merge(normalized, 4 - rating, Integer::sum);
            }
        }));
        return signal;
    }

    private List<String> targetCompanies(User user, List<Session> sessions) {
        Set<String> interviewerIds = new LinkedHashSet<>();
        sessions.stream().map(Session::getInterviewerId).filter(item -> item != null && !item.isBlank()).forEach(interviewerIds::add);
        user.getFavoriteInterviewerIds().stream().filter(item -> item != null && !item.isBlank()).forEach(interviewerIds::add);
        Map<String, Integer> companies = new HashMap<>();
        if (!interviewerIds.isEmpty()) {
            userRepository.findAllById(interviewerIds).forEach(interviewer -> {
                String company = normalize(interviewer.getCompany());
                if (!company.isBlank()) companies.merge(company, 1, Integer::sum);
            });
        }
        return companies.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(4)
                .map(Map.Entry::getKey)
                .toList();
    }

    private String personaLabel(User user) {
        String role = normalize(user.getCurrentRole());
        if (!role.isBlank()) return role + " preparation";
        return user.hasRole("INTERVIEWER") ? "Interviewer and candidate preparation" : "Candidate preparation";
    }

    private List<String> orderedKeys(Map<String, Integer> source, int limit) {
        return source.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .limit(limit)
                .toList();
    }

    private List<String> splitOptions(Collection<String> values) {
        if (values == null) return List.of();
        return values.stream()
                .filter(Objects::nonNull)
                .flatMap(value -> splitOptions(value).stream())
                .toList();
    }

    private List<String> splitOptions(String value) {
        if (value == null || value.isBlank()) return List.of();
        return java.util.Arrays.stream(value.split(","))
                .map(this::normalize)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private void addSignal(Map<String, Integer> sink, List<String> values, int weight) {
        values.forEach(value -> sink.merge(value, weight, Integer::sum));
    }

    private boolean isStatus(Session session, String status) {
        return normalize(session.getStatus()).equalsIgnoreCase(normalize(status));
    }

    private String normalize(String value) {
        if (value == null) return "";
        String normalized = value.trim().replaceAll("\\s+", " ");
        if (normalized.length() <= 4 && normalized.equals(normalized.toUpperCase(Locale.ROOT))) return normalized;
        return normalized;
    }
}
