package com.interview.platform.service;

import com.interview.platform.dto.MarketplaceDtos;
import com.interview.platform.exception.ResourceNotFoundException;
import com.interview.platform.model.Feedback;
import com.interview.platform.model.Session;
import com.interview.platform.model.User;
import com.interview.platform.repository.FeedbackRepository;
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

    public PrepService(UserRepository userRepository,
                       SessionRepository sessionRepository,
                       FeedbackRepository feedbackRepository) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.feedbackRepository = feedbackRepository;
    }

    public MarketplaceDtos.PrepHubResponse buildHub(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        List<Session> sessions = sessionRepository.findByCandidateId(userId);
        List<String> sessionIds = sessions.stream().map(Session::getId).filter(Objects::nonNull).toList();
        List<Feedback> feedback = sessionIds.isEmpty() ? List.of() : feedbackRepository.findBySessionIdIn(sessionIds);

        Map<String, Integer> topicSignal = buildTopicSignal(user, sessions, feedback);
        List<String> primaryTopics = orderedKeys(topicSignal, 6);
        Map<String, Integer> weakTopicSignal = weakTopicSignal(feedback);
        List<String> weakTopics = orderedKeys(weakTopicSignal, 4);
        List<String> targetCompanies = targetCompanies(user, sessions);
        int sessionsCompleted = (int) sessions.stream().filter(item -> isStatus(item, "COMPLETED")).count();
        int prepCoverage = coverageScore(primaryTopics.size(), weakTopics.size(), sessionsCompleted, hasResume(user));

        List<MarketplaceDtos.PrepTrack> companyTracks = buildCompanyTracks(targetCompanies, primaryTopics, sessionsCompleted, hasResume(user));
        List<MarketplaceDtos.PrepTrack> behavioralTracks = buildBehavioralTracks(user, feedback, sessionsCompleted);
        List<MarketplaceDtos.PrepTrack> codingTracks = buildCodingTracks(primaryTopics, weakTopics, sessionsCompleted);
        List<MarketplaceDtos.PrepTrack> roleTracks = buildRoleTracks(user, primaryTopics, sessionsCompleted, feedback.size());
        List<MarketplaceDtos.PrepResource> resources = buildResources(user, primaryTopics, weakTopics, sessionsCompleted, prepCoverage);
        List<MarketplaceDtos.PrepResource> quickWins = buildQuickWins(user, primaryTopics, weakTopics, sessionsCompleted, prepCoverage);

        return new MarketplaceDtos.PrepHubResponse(
                personaLabel(user),
                primaryTopics,
                targetCompanies,
                companyTracks,
                behavioralTracks,
                codingTracks,
                roleTracks,
                resources,
                quickWins
        );
    }

    private List<MarketplaceDtos.PrepTrack> buildCompanyTracks(List<String> companies,
                                                               List<String> topics,
                                                               int sessionsCompleted,
                                                               boolean hasResume) {
        List<String> scopedCompanies = companies.isEmpty() ? List.of("Target interviewer companies") : companies;
        List<MarketplaceDtos.PrepTrack> tracks = new ArrayList<>();
        int index = 0;
        for (String company : scopedCompanies.stream().limit(3).toList()) {
            int progress = clamp(32 + (sessionsCompleted * 6) + (hasResume ? 10 : 0) - (index * 4));
            List<String> focus = mergeUnique(
                    topics.stream().limit(3).toList(),
                    List.of("Role expectations", "Round-specific depth")
            ).stream().limit(5).toList();
            tracks.add(new MarketplaceDtos.PrepTrack(
                    company + " interview loop",
                    "Practice with interviewer-aligned expectations and strengthen outcome stories for this company context.",
                    focus,
                    progress,
                    readinessStage(progress),
                    List.of("Company-aware", sessionsCompleted > 0 ? "History-aware" : "Profile-aware")
            ));
            index += 1;
        }
        return tracks;
    }

    private List<MarketplaceDtos.PrepTrack> buildBehavioralTracks(User user, List<Feedback> feedback, int sessionsCompleted) {
        int clarityScore = clamp(35 + (sessionsCompleted * 5) + (positiveFeedbackCount(feedback) * 4));
        int impactScore = clamp(30 + (sessionsCompleted * 6) + (hasResume(user) ? 12 : 0));
        List<MarketplaceDtos.PrepTrack> tracks = new ArrayList<>();
        tracks.add(new MarketplaceDtos.PrepTrack(
                "Behavioral narrative bank",
                "Turn real interview events into concise, repeatable stories aligned to ownership, ambiguity, and impact.",
                List.of("STAR compression", "Conflict resolution", "Leadership without authority", "Failure-to-learning narrative"),
                clarityScore,
                readinessStage(clarityScore),
                List.of("Role-aware", "Feedback-aware")
        ));
        tracks.add(new MarketplaceDtos.PrepTrack(
                "Hiring manager impact stories",
                "Prioritize scope, decision quality, and measurable outcomes across your recent projects.",
                List.of("Prioritization tradeoffs", "Outcome metrics", "Cross-team influence", "Decision rationale"),
                impactScore,
                readinessStage(impactScore),
                List.of(hasResume(user) ? "Resume-aware" : "Profile-aware", sessionsCompleted > 0 ? "Interview-history-aware" : "Foundation mode")
        ));
        return tracks;
    }

    private List<MarketplaceDtos.PrepTrack> buildCodingTracks(List<String> topics,
                                                              List<String> weakTopics,
                                                              int sessionsCompleted) {
        List<String> priorityTopics = mergeUnique(weakTopics, topics).stream().limit(5).toList();
        int algorithmScore = clamp(28 + (sessionsCompleted * 6) + (topics.size() * 5));
        int designScore = clamp(24 + (sessionsCompleted * 7) + (containsTopic(topics, "System Design") ? 15 : 0));
        return List.of(
                new MarketplaceDtos.PrepTrack(
                        "Coding depth plan",
                        "Focus on weak areas first, then lock reliable patterns for high-frequency coding rounds.",
                        mergeUnique(priorityTopics, List.of("Edge-case checks", "Complexity articulation", "Time-boxed repetition")).stream().limit(6).toList(),
                        algorithmScore,
                        readinessStage(algorithmScore),
                        List.of("Topic-aware", weakTopics.isEmpty() ? "Balanced plan" : "Weakness-prioritized")
                ),
                new MarketplaceDtos.PrepTrack(
                        "System and machine design readiness",
                        "Strengthen architecture reasoning, requirement framing, and operational tradeoff communication.",
                        List.of("Capacity planning", "Reliability posture", "Data-flow decomposition", "Extensibility strategy"),
                        designScore,
                        readinessStage(designScore),
                        List.of("Role-aware", "Company-loop aligned")
                )
        );
    }

    private List<MarketplaceDtos.PrepTrack> buildRoleTracks(User user,
                                                            List<String> topics,
                                                            int sessionsCompleted,
                                                            int feedbackCount) {
        String currentRole = normalize(user.getCurrentRole());
        String roleLabel = currentRole.isBlank() ? "Current role focus" : currentRole;
        int roleScore = clamp(26 + (sessionsCompleted * 7) + (feedbackCount * 3) + Math.min(18, safe(user.getYearsExperience()) * 2));
        int transitionScore = clamp(roleScore + (containsTopic(topics, "Behavioral") ? 6 : 0) - 4);
        return List.of(
                new MarketplaceDtos.PrepTrack(
                        roleLabel + " transition pack",
                        "Build role-specific preparation loops aligned to the responsibilities you are targeting next.",
                        List.of("Role rubric mapping", "Scope communication", "Execution tradeoffs", "Stakeholder alignment"),
                        roleScore,
                        readinessStage(roleScore),
                        List.of("Role-aware", "Experience-aware")
                ),
                new MarketplaceDtos.PrepTrack(
                        "Session-to-session improvement loop",
                        "Use interview outcomes and feedback trends to prioritize exactly what to practice next.",
                        List.of("Feedback tagging", "Weekly focus ladder", "Retrospective cadence", "Confidence calibration"),
                        transitionScore,
                        readinessStage(transitionScore),
                        List.of("History-aware", feedbackCount > 0 ? "Feedback-driven" : "Baseline mode")
                )
        );
    }

    private List<MarketplaceDtos.PrepResource> buildResources(User user,
                                                              List<String> primaryTopics,
                                                              List<String> weakTopics,
                                                              int sessionsCompleted,
                                                              int coverageScore) {
        List<MarketplaceDtos.PrepResource> resources = new ArrayList<>();
        resources.add(new MarketplaceDtos.PrepResource(
                "Personal prep brief",
                "Dashboard brief",
                "A consolidated summary of your highest-priority topics, role context, and readiness gaps from real activity.",
                "Review brief",
                coverageScore,
                true,
                List.of("Personalized", "Live profile")
        ));
        resources.add(new MarketplaceDtos.PrepResource(
                hasResume(user) ? "Resume-to-interview bridge" : "Resume foundation checklist",
                "Resume",
                hasResume(user)
                        ? "Translate your uploaded resume into targeted interview talking points for role and company loops."
                        : "Upload a resume to generate tighter, role-aware talking points and topic recommendations.",
                hasResume(user) ? "Generate talking points" : "Upload resume",
                hasResume(user) ? clamp(68 + (sessionsCompleted * 3)) : 22,
                true,
                List.of("Resume-aware", hasResume(user) ? "Actionable" : "Unlock")
        ));
        resources.add(new MarketplaceDtos.PrepResource(
                "Topic drill queue",
                "Practice queue",
                "Structured drills sequenced by weak-signal topics from your interviews and feedback history.",
                "Start drills",
                clamp(30 + (primaryTopics.size() * 6)),
                true,
                mergeUnique(weakTopics.stream().limit(3).toList(), List.of("Feedback-derived"))
        ));
        resources.add(new MarketplaceDtos.PrepResource(
                "Session replay checklist",
                "Reflection",
                "Use recent sessions to capture misses, update examples, and convert outcomes into next-round wins.",
                "Open checklist",
                clamp(24 + (sessionsCompleted * 8)),
                sessionsCompleted > 0,
                List.of("History-aware", "Continuous improvement")
        ));
        return resources;
    }

    private List<MarketplaceDtos.PrepResource> buildQuickWins(User user,
                                                              List<String> primaryTopics,
                                                              List<String> weakTopics,
                                                              int sessionsCompleted,
                                                              int coverageScore) {
        String topTopic = primaryTopics.isEmpty() ? "core interview topics" : primaryTopics.get(0);
        String weakTopic = weakTopics.isEmpty() ? topTopic : weakTopics.get(0);
        List<MarketplaceDtos.PrepResource> wins = new ArrayList<>();
        wins.add(new MarketplaceDtos.PrepResource(
                "30-minute " + weakTopic + " fix",
                "Quick win",
                "Run one focused practice loop on " + weakTopic + " and capture one takeaway to carry into the next interview.",
                "Start now",
                clamp(coverageScore - 6),
                true,
                List.of("Topic-priority", "Fast iteration")
        ));
        wins.add(new MarketplaceDtos.PrepResource(
                "Role intro refresh",
                "Quick win",
                "Refine your 60-second role summary to align with the outcomes and scope expected in your target interviews.",
                "Refine intro",
                clamp(36 + (safe(user.getYearsExperience()) * 4)),
                true,
                List.of("Role-aware")
        ));
        wins.add(new MarketplaceDtos.PrepResource(
                hasResume(user) ? "Resume bullet sharpening" : "Resume upload milestone",
                "Quick win",
                hasResume(user)
                        ? "Improve two high-impact resume bullets and map each to one interview story."
                        : "Upload your resume to unlock stronger personalized prep suggestions.",
                hasResume(user) ? "Sharpen bullets" : "Upload resume",
                hasResume(user) ? 58 : 18,
                true,
                List.of("Resume-aware")
        ));
        if (sessionsCompleted > 0) {
            wins.add(new MarketplaceDtos.PrepResource(
                    "Latest session debrief",
                    "Quick win",
                    "Tag what went well and what slipped in your latest completed session, then schedule one targeted retry.",
                    "Debrief now",
                    clamp(42 + (sessionsCompleted * 5)),
                    true,
                    List.of("History-aware", "Feedback loop")
            ));
        }
        return wins.stream().limit(4).toList();
    }

    private Map<String, Integer> buildTopicSignal(User user, List<Session> sessions, List<Feedback> feedback) {
        Map<String, Integer> signal = new LinkedHashMap<>();
        addSignal(signal, splitOptions(user.getInterviewTopics()), 5);
        addSignal(signal, splitOptions(user.getPreferredDomains()), 4);
        addSignal(signal, splitOptions(user.getSkills()), 3);
        sessions.forEach(session -> addSignal(signal, splitOptions(session.getTopics()), isStatus(session, "COMPLETED") ? 4 : 2));
        feedback.forEach(item -> {
            item.getTopicFeedback().forEach(topic -> {
                String normalized = normalize(topic.getTopic());
                if (normalized.isBlank()) return;
                int rating = safe(topic.getRating());
                int weight = rating <= 2 ? 8 : (rating == 3 ? 6 : 3);
                signal.merge(normalized, weight, Integer::sum);
                topic.getSkillRatings().forEach((skill, score) -> {
                    if (score == null) return;
                    int skillWeight = score <= 2 ? 6 : (score == 3 ? 4 : 2);
                    String normalizedSkill = normalize(skill);
                    if (!normalizedSkill.isBlank()) signal.merge(normalizedSkill, skillWeight, Integer::sum);
                });
            });
        });
        return signal;
    }

    private Map<String, Integer> weakTopicSignal(List<Feedback> feedback) {
        Map<String, Integer> weak = new LinkedHashMap<>();
        feedback.forEach(item -> item.getTopicFeedback().forEach(topic -> {
            String normalizedTopic = normalize(topic.getTopic());
            int rating = safe(topic.getRating());
            if (!normalizedTopic.isBlank() && rating > 0 && rating <= 3) {
                weak.merge(normalizedTopic, (4 - rating) * 3, Integer::sum);
            }
            topic.getSkillRatings().forEach((skill, score) -> {
                if (score == null || score > 3) return;
                String normalizedSkill = normalize(skill);
                if (normalizedSkill.isBlank()) return;
                weak.merge(normalizedSkill, (4 - score) * 2, Integer::sum);
            });
        }));
        return weak;
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
        if (companies.isEmpty()) {
            userRepository.findByRoleOrderByAverageRatingDesc("INTERVIEWER").stream()
                    .limit(20)
                    .map(User::getCompany)
                    .map(this::normalize)
                    .filter(company -> !company.isBlank())
                    .forEach(company -> companies.merge(company, 1, Integer::sum));
        }
        return companies.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(4)
                .map(Map.Entry::getKey)
                .toList();
    }

    private String personaLabel(User user) {
        String role = normalize(user.getCurrentRole());
        if (!role.isBlank()) {
            return role + " interview preparation";
        }
        if (user.hasRole("INTERVIEWER")) {
            return "Interviewer and interviewee preparation";
        }
        return "Interviewee preparation";
    }

    private int positiveFeedbackCount(List<Feedback> feedback) {
        int count = 0;
        for (Feedback item : feedback) {
            if (item.getRating() >= 4) count += 1;
        }
        return count;
    }

    private int coverageScore(int topicCount, int weakTopicCount, int completedSessions, boolean hasResume) {
        int score = 24 + (topicCount * 8) + (completedSessions * 7) + (hasResume ? 12 : 0) - (weakTopicCount * 3);
        return clamp(score);
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

    private List<String> mergeUnique(List<String> left, List<String> right) {
        Set<String> merged = new LinkedHashSet<>();
        if (left != null) merged.addAll(left.stream().map(this::normalize).filter(item -> !item.isBlank()).toList());
        if (right != null) merged.addAll(right.stream().map(this::normalize).filter(item -> !item.isBlank()).toList());
        return new ArrayList<>(merged);
    }

    private boolean containsTopic(List<String> values, String target) {
        String normalized = normalize(target).toLowerCase(Locale.ROOT);
        return values.stream().map(item -> normalize(item).toLowerCase(Locale.ROOT)).anyMatch(item -> item.contains(normalized));
    }

    private boolean hasResume(User user) {
        return user.getResumeUrl() != null && !user.getResumeUrl().isBlank();
    }

    private boolean isStatus(Session session, String status) {
        return normalize(session.getStatus()).equalsIgnoreCase(normalize(status));
    }

    private int safe(Integer value) {
        return value == null ? 0 : value;
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private String readinessStage(int score) {
        if (score >= 75) return "Strong";
        if (score >= 50) return "Building";
        if (score >= 30) return "Foundation";
        return "Starting";
    }

    private String normalize(String value) {
        if (value == null) return "";
        return value.trim().replaceAll("\\s+", " ");
    }
}
