package com.interview.platform.service;

import com.interview.platform.dto.PrepDtos;
import com.interview.platform.exception.ResourceNotFoundException;
import com.interview.platform.model.Feedback;
import com.interview.platform.model.ResumeIntelligenceProfile;
import com.interview.platform.model.Session;
import com.interview.platform.model.User;
import com.interview.platform.repository.FeedbackRepository;
import com.interview.platform.repository.ResumeIntelligenceProfileRepository;
import com.interview.platform.repository.SessionRepository;
import com.interview.platform.repository.UserRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ResumeIntelligenceService {
    private static final long MAX_DOCUMENT_SIZE_BYTES = 10L * 1024 * 1024;
    private static final int MAX_RESUME_HISTORY = 20;
    private static final int MAX_MATCH_HISTORY = 12;
    private static final int MAX_PROGRESS_POINTS = 24;
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("MMM d")
            .withLocale(Locale.ENGLISH)
            .withZone(ZoneOffset.UTC);

    private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", Pattern.CASE_INSENSITIVE);
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?:\\+?\\d{1,3}[\\s-]?)?(?:\\(?\\d{3}\\)?[\\s-]?)\\d{3}[\\s-]?\\d{4}");
    private static final Pattern DATE_PATTERN = Pattern.compile("\\b(19|20)\\d{2}\\b");
    private static final Pattern QUANTIFIED_PATTERN = Pattern.compile("(?i)(\\d+%|\\d+\\.?\\d*\\s?(x|k|m|million|billion)|\\$\\s?\\d+|₹\\s?\\d+|\\d+\\s?(users|customers|ms|seconds|days|months|years))");

    private static final Set<String> SKILL_KEYWORDS = Set.of(
            "java", "spring", "spring boot", "microservices", "rest api", "sql", "nosql", "mongodb", "postgresql", "mysql",
            "docker", "kubernetes", "aws", "gcp", "azure", "react", "node.js", "node", "typescript", "javascript", "python",
            "c++", "c#", "go", "redis", "kafka", "system design", "data structures", "algorithms", "dsa", "oop", "design patterns",
            "graphql", "html", "css", "tailwind", "next.js", "express", "hibernate", "jpa", "unit testing", "integration testing",
            "ci/cd", "jenkins", "terraform", "linux", "problem solving", "communication", "leadership", "product thinking",
            "behavioral", "debugging", "performance optimization", "api design", "distributed systems", "scalability"
    );

    private static final Set<String> TECHNOLOGY_KEYWORDS = Set.of(
            "java", "spring boot", "react", "node.js", "typescript", "javascript", "python", "go", "c++", "c#",
            "aws", "gcp", "azure", "docker", "kubernetes", "postgresql", "mysql", "mongodb", "redis", "kafka",
            "graphql", "rest", "microservices", "terraform", "jenkins", "git", "linux", "html", "css", "tailwind"
    );

    private static final Set<String> STOP_WORDS = Set.of(
            "the", "and", "for", "with", "that", "from", "this", "have", "will", "your", "you", "our", "are", "was", "were", "has", "had", "not",
            "but", "into", "than", "then", "also", "their", "them", "who", "what", "when", "where", "while", "about", "over", "under", "across",
            "using", "used", "build", "built", "work", "worked", "team", "teams", "role", "roles", "project", "projects", "experience", "skills",
            "resume", "interview", "candidate", "engineer", "developer", "software", "company", "summary", "objective"
    );

    private final ResumeIntelligenceProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final FeedbackRepository feedbackRepository;

    public ResumeIntelligenceService(ResumeIntelligenceProfileRepository profileRepository,
                                     UserRepository userRepository,
                                     SessionRepository sessionRepository,
                                     FeedbackRepository feedbackRepository) {
        this.profileRepository = profileRepository;
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.feedbackRepository = feedbackRepository;
    }

    public void processUploadedResume(String userId,
                                      MultipartFile file,
                                      String resumeUrl,
                                      String contentType,
                                      String fileName) {
        String text = extractTextFromDocument(file, true);
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        List<Session> sessions = sessionRepository.findByCandidateId(userId);
        List<Feedback> feedback = loadFeedbackForSessions(sessions);

        ResumeIntelligenceProfile profile = loadOrCreateProfile(userId);
        ResumeIntelligenceProfile.ParsedResume parsedResume = parseResumeText(text);
        ResumeIntelligenceProfile.AtsSummary atsSummary = analyzeAts(parsedResume, text, user, sessions, feedback, null);
        int readinessScore = computeReadinessScore(atsSummary.getAtsScore(), sessions, feedback, parsedResume, atsSummary);

        ResumeIntelligenceProfile.ResumeVersion version = new ResumeIntelligenceProfile.ResumeVersion();
        version.setId(UUID.randomUUID().toString());
        version.setResumeUrl(trimToNull(resumeUrl));
        version.setContentType(normalizeContentType(contentType, fileName));
        version.setFileName(trimToNull(fileName));
        version.setFileSizeBytes(file == null ? null : file.getSize());
        version.setUploadedAt(Instant.now());
        version.setRoleHint(trimToNull(user.getCurrentRole()));
        version.setParsedResume(parsedResume);
        version.setAtsSummary(atsSummary);
        version.setInterviewReadinessScore(readinessScore);

        List<ResumeIntelligenceProfile.ResumeVersion> versions = new ArrayList<>(profile.getVersions());
        versions.add(0, version);
        if (versions.size() > MAX_RESUME_HISTORY) {
            versions = versions.subList(0, MAX_RESUME_HISTORY);
        }
        profile.setVersions(versions);
        profile.setActiveVersionId(version.getId());
        captureProgress(profile, atsSummary.getAtsScore(), readinessScore, completedModules(sessions, feedback));
        profile.setUpdatedAt(Instant.now());
        profileRepository.save(profile);
    }

    public void clearActiveResume(String userId) {
        ResumeIntelligenceProfile profile = loadOrCreateProfile(userId);
        profile.setActiveVersionId(null);
        profile.setUpdatedAt(Instant.now());
        profileRepository.save(profile);
    }

    public PrepDtos.PrepIntelligenceDashboard activateResumeVersion(String userId, String resumeVersionId) {
        ResumeIntelligenceProfile profile = loadOrCreateProfile(userId);
        if (resumeVersionId == null || resumeVersionId.isBlank()) {
            throw new IllegalArgumentException("Choose a resume version.");
        }
        boolean exists = profile.getVersions().stream().anyMatch(item -> resumeVersionId.equals(item.getId()));
        if (!exists) {
            throw new ResourceNotFoundException("Resume version not found");
        }
        profile.setActiveVersionId(resumeVersionId);
        profile.setUpdatedAt(Instant.now());
        profileRepository.save(profile);
        return buildDashboard(userId);
    }

    public PrepDtos.PrepIntelligenceDashboard buildDashboard(String userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        ResumeIntelligenceProfile profile = loadOrCreateProfile(userId);
        List<Session> sessions = sessionRepository.findByCandidateId(userId);
        List<Feedback> feedback = loadFeedbackForSessions(sessions);

        ResumeIntelligenceProfile.ResumeVersion active = activeVersion(profile);
        List<PrepDtos.ResumeVersionSummary> history = profile.getVersions().stream()
                .sorted(Comparator.comparing(ResumeIntelligenceProfile.ResumeVersion::getUploadedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(item -> toSummary(item, Objects.equals(item.getId(), profile.getActiveVersionId())))
                .toList();

        if (active == null) {
            return new PrepDtos.PrepIntelligenceDashboard(
                    null,
                    history,
                    new PrepDtos.ResumeParsedData(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()),
                    new PrepDtos.AtsAnalysis(0, 0, 0, 0, 0, 0, List.of(), List.of(), List.of("Upload a PDF or DOCX resume to start ATS analysis."), Map.of()),
                    null,
                    trendFromProgress(profile, true),
                    trendFromProgress(profile, false),
                    baseReadinessWithoutResume(sessions, feedback),
                    Map.of(),
                    topicReadinessMap(sessions, feedback, List.of(), List.of()),
                    defaultRoadmap("No active resume"),
                    weakAreas(feedback, List.of()),
                    recommendedTopics(sessions, feedback, List.of(), List.of()),
                    recommendationCardsWithoutResume(sessions, feedback),
                    completedModules(sessions, feedback)
            );
        }

        ResumeIntelligenceProfile.AtsSummary ats = active.getAtsSummary();
        ResumeIntelligenceProfile.ParsedResume parsed = active.getParsedResume();
        int readiness = computeReadinessScore(ats.getAtsScore(), sessions, feedback, parsed, ats);
        PrepDtos.JobMatchResult latestMatch = toJobMatch(latestMatch(active));

        return new PrepDtos.PrepIntelligenceDashboard(
                toSummary(active, true),
                history,
                toParsed(parsed),
                toAts(ats),
                latestMatch,
                trendFromProgress(profile, true),
                trendFromProgress(profile, false),
                readiness,
                skillGapMap(ats.getMissingSkills()),
                topicReadinessMap(sessions, feedback, parsed.getSkills(), parsed.getTechnologies()),
                roadmapFromContext(sessions, feedback, ats),
                weakAreas(feedback, ats.getMissingSkills()),
                recommendedTopics(sessions, feedback, parsed.getSkills(), ats.getMissingSkills()),
                buildRecommendationCards(active, sessions, feedback, latestMatch),
                completedModules(sessions, feedback)
        );
    }

    public PrepDtos.JobMatchResult matchJobDescription(String userId, String jobDescription, String roleHint) {
        String cleaned = normalizeText(jobDescription);
        if (cleaned.isBlank()) {
            throw new IllegalArgumentException("Paste a job description before running match analysis.");
        }
        ResumeIntelligenceProfile profile = loadOrCreateProfile(userId);
        ResumeIntelligenceProfile.ResumeVersion active = activeVersion(profile);
        if (active == null) {
            throw new IllegalArgumentException("Upload a resume before running job description matching.");
        }

        ResumeIntelligenceProfile.JobMatchSnapshot snapshot = computeJobMatch(active, cleaned, roleHint);
        List<ResumeIntelligenceProfile.JobMatchSnapshot> matches = new ArrayList<>(active.getJobMatches());
        matches.add(0, snapshot);
        if (matches.size() > MAX_MATCH_HISTORY) {
            matches = matches.subList(0, MAX_MATCH_HISTORY);
        }
        active.setJobMatches(matches);

        List<Session> sessions = sessionRepository.findByCandidateId(userId);
        List<Feedback> feedback = loadFeedbackForSessions(sessions);
        int readiness = computeReadinessScore(active.getAtsSummary().getAtsScore(), sessions, feedback, active.getParsedResume(), active.getAtsSummary());
        active.setInterviewReadinessScore(readiness);
        captureProgress(profile, active.getAtsSummary().getAtsScore(), readiness, completedModules(sessions, feedback));

        profile.setUpdatedAt(Instant.now());
        profileRepository.save(profile);
        return toJobMatch(snapshot);
    }

    public PrepDtos.JobMatchResult matchJobDescriptionUpload(String userId, MultipartFile file, String roleHint) {
        String text = extractTextFromDocument(file, false);
        return matchJobDescription(userId, text, roleHint == null ? file == null ? null : file.getOriginalFilename() : roleHint);
    }

    private ResumeIntelligenceProfile loadOrCreateProfile(String userId) {
        ResumeIntelligenceProfile profile = profileRepository.findByUserId(userId).orElseGet(() -> {
            ResumeIntelligenceProfile created = new ResumeIntelligenceProfile();
            created.setUserId(userId);
            created.setCreatedAt(Instant.now());
            created.setUpdatedAt(Instant.now());
            return created;
        });
        if (profile.getCreatedAt() == null) profile.setCreatedAt(Instant.now());
        if (profile.getUpdatedAt() == null) profile.setUpdatedAt(Instant.now());
        return profile;
    }

    private List<Feedback> loadFeedbackForSessions(List<Session> sessions) {
        List<String> sessionIds = sessions.stream()
                .map(Session::getId)
                .filter(Objects::nonNull)
                .filter(item -> !item.isBlank())
                .toList();
        if (sessionIds.isEmpty()) return List.of();
        return feedbackRepository.findBySessionIdIn(sessionIds);
    }

    private String extractTextFromDocument(MultipartFile file, boolean strictResumeFormats) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Choose a file before running resume intelligence.");
        }
        if (file.getSize() > MAX_DOCUMENT_SIZE_BYTES) {
            throw new IllegalArgumentException("File size exceeds 10 MB limit.");
        }
        String contentType = normalizeContentType(file.getContentType(), file.getOriginalFilename());
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new IllegalArgumentException("Could not read the uploaded file.");
        }

        if (isPdf(contentType, file.getOriginalFilename())) {
            return parsePdf(bytes);
        }
        if (isDocx(contentType, file.getOriginalFilename())) {
            return parseDocx(bytes);
        }
        if (!strictResumeFormats && isText(contentType, file.getOriginalFilename())) {
            return sanitizeDocumentText(new String(bytes));
        }
        if (strictResumeFormats) {
            throw new IllegalArgumentException("Only PDF and DOCX resumes are supported.");
        }
        throw new IllegalArgumentException("Upload a JD as TXT, PDF, or DOCX.");
    }

    private String parsePdf(byte[] bytes) {
        try (PDDocument document = PDDocument.load(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return sanitizeDocumentText(stripper.getText(document));
        } catch (IOException ex) {
            throw new IllegalArgumentException("Could not parse PDF content.");
        }
    }

    private String parseDocx(byte[] bytes) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            StringBuilder buffer = new StringBuilder();
            document.getParagraphs().forEach(paragraph -> {
                String text = paragraph.getText();
                if (text != null && !text.isBlank()) buffer.append(text).append('\n');
            });
            return sanitizeDocumentText(buffer.toString());
        } catch (IOException ex) {
            throw new IllegalArgumentException("Could not parse DOCX content.");
        }
    }

    private ResumeIntelligenceProfile.ParsedResume parseResumeText(String text) {
        String normalizedText = sanitizeDocumentText(text);
        List<String> lines = Arrays.stream(normalizedText.split("\\r?\\n"))
                .map(this::normalizeText)
                .filter(item -> !item.isBlank())
                .toList();
        Map<String, List<String>> sections = splitSections(lines);

        ResumeIntelligenceProfile.ParsedResume parsed = new ResumeIntelligenceProfile.ParsedResume();
        parsed.setSkills(mergeUnique(
                detectKeywordsFromLexicon(normalizedText, SKILL_KEYWORDS),
                splitLooseItems(sections.get("skills"))
        ));
        parsed.setTechnologies(mergeUnique(
                detectKeywordsFromLexicon(normalizedText, TECHNOLOGY_KEYWORDS),
                splitLooseItems(sections.get("technologies"))
        ));
        parsed.setEducation(filterByPattern(coalesceSection(sections, "education"), Pattern.compile("(?i)(b\\.?tech|bachelor|master|phd|university|college|gpa|degree|school|cgpa)"), 10));
        parsed.setExperience(trimList(coalesceSection(sections, "experience"), 14));
        parsed.setProjects(trimList(coalesceSection(sections, "projects"), 12));
        parsed.setCertifications(filterByPattern(coalesceSection(sections, "certifications"), Pattern.compile("(?i)(certif|aws|gcp|azure|scrum|oracle|coursera|udemy|license)"), 10));
        parsed.setCompanies(extractCompanies(normalizedText, parsed.getExperience(), parsed.getProjects()));
        parsed.setKeywords(extractTopKeywords(normalizedText, 22));
        return parsed;
    }

    private Map<String, List<String>> splitSections(List<String> lines) {
        Map<String, List<String>> sections = new LinkedHashMap<>();
        String section = "general";
        sections.put(section, new ArrayList<>());
        for (String rawLine : lines) {
            String line = normalizeText(rawLine);
            String heading = headingKey(line);
            if (heading != null) {
                section = heading;
                sections.computeIfAbsent(section, key -> new ArrayList<>());
                continue;
            }
            sections.computeIfAbsent(section, key -> new ArrayList<>()).add(line);
        }
        return sections;
    }

    private String headingKey(String line) {
        String cleaned = line.replace(':', ' ').trim().toLowerCase(Locale.ROOT);
        if (cleaned.matches("^(skills|technical skills|core skills|key skills)$")) return "skills";
        if (cleaned.matches("^(technologies|tech stack|tools)$")) return "technologies";
        if (cleaned.matches("^(experience|professional experience|work experience|employment)$")) return "experience";
        if (cleaned.matches("^(projects|project experience)$")) return "projects";
        if (cleaned.matches("^(education|academic background|academics)$")) return "education";
        if (cleaned.matches("^(certifications|certificates|licenses)$")) return "certifications";
        return null;
    }

    private ResumeIntelligenceProfile.AtsSummary analyzeAts(ResumeIntelligenceProfile.ParsedResume parsed,
                                                            String text,
                                                            User user,
                                                            List<Session> sessions,
                                                            List<Feedback> feedback,
                                                            String overrideRoleHint) {
        ResumeIntelligenceProfile.AtsSummary summary = new ResumeIntelligenceProfile.AtsSummary();
        int completeness = completenessScore(parsed);
        int diversity = keywordDiversityScore(text);
        int quantifiedSignals = countQuantifiedSignals(text);
        int formatting = formattingScore(text, quantifiedSignals);
        int keywordStrength = keywordStrengthScore(parsed, diversity);
        List<String> missingSkills = missingSkills(parsed, roleContext(user, overrideRoleHint));
        int ats = clamp((int) Math.round((completeness * 0.34) + (keywordStrength * 0.34) + (formatting * 0.32)));

        Map<String, Integer> components = new LinkedHashMap<>();
        components.put("completeness", completeness);
        components.put("keywordStrength", keywordStrength);
        components.put("formatting", formatting);
        components.put("diversity", diversity);
        components.put("quantifiedSignals", Math.min(100, quantifiedSignals * 16));

        summary.setAtsScore(ats);
        summary.setCompletenessScore(completeness);
        summary.setKeywordStrength(keywordStrength);
        summary.setFormattingQualityScore(formatting);
        summary.setKeywordDiversityScore(diversity);
        summary.setQuantifiedAchievementSignals(quantifiedSignals);
        summary.setMissingSkills(missingSkills);
        summary.setFormattingQualityIndicators(formattingIndicators(text, quantifiedSignals, diversity));
        summary.setRecommendations(atsRecommendations(ats, missingSkills, quantifiedSignals, diversity, sessions, feedback));
        summary.setComponentScores(components);
        return summary;
    }

    private ResumeIntelligenceProfile.JobMatchSnapshot computeJobMatch(ResumeIntelligenceProfile.ResumeVersion active,
                                                                       String jobDescription,
                                                                       String roleHint) {
        Set<String> jdKeywords = new LinkedHashSet<>(extractTopKeywords(jobDescription, 28));
        jdKeywords.addAll(detectKeywordsFromLexicon(jobDescription, SKILL_KEYWORDS));
        jdKeywords.addAll(detectKeywordsFromLexicon(jobDescription, TECHNOLOGY_KEYWORDS));

        Set<String> resumeKeywords = new LinkedHashSet<>();
        resumeKeywords.addAll(normalizeAll(active.getParsedResume().getSkills()));
        resumeKeywords.addAll(normalizeAll(active.getParsedResume().getTechnologies()));
        resumeKeywords.addAll(normalizeAll(active.getParsedResume().getKeywords()));

        List<String> matched = jdKeywords.stream().filter(item -> containsToken(resumeKeywords, item)).limit(20).toList();
        List<String> missing = jdKeywords.stream().filter(item -> !containsToken(resumeKeywords, item)).limit(20).toList();

        int referenceCount = Math.max(1, Math.min(26, jdKeywords.size()));
        int coverage = clamp((int) Math.round((matched.size() * 100.0) / referenceCount));
        int atsBoost = clamp((int) Math.round(active.getAtsSummary().getAtsScore() * 0.3));
        int matchPercent = clamp((int) Math.round((coverage * 0.7) + atsBoost));

        List<String> strengths = matched.stream().limit(5).map(item -> "Strong alignment in " + titleCase(item)).toList();
        if (strengths.isEmpty()) strengths = List.of("Resume and JD overlap is limited; add role-specific keywords.");

        ResumeIntelligenceProfile.JobMatchSnapshot snapshot = new ResumeIntelligenceProfile.JobMatchSnapshot();
        snapshot.setId(UUID.randomUUID().toString());
        snapshot.setSourceLabel(trimToNull(roleHint));
        snapshot.setMatchPercent(matchPercent);
        snapshot.setKeywordCoverage(coverage);
        snapshot.setMatchedKeywords(matched);
        snapshot.setMissingKeywords(missing);
        snapshot.setMatchingStrengths(strengths);
        snapshot.setEvaluatedAt(Instant.now());
        snapshot.setSummary(jobMatchSummary(matchPercent, coverage, matched.size(), missing.size()));
        return snapshot;
    }

    private String jobMatchSummary(int matchPercent, int coverage, int matchedCount, int missingCount) {
        if (matchPercent >= 75) return "Strong JD alignment with good keyword and experience coverage.";
        if (matchPercent >= 55) return "Moderate alignment. Add targeted keywords and impact-focused bullets.";
        if (coverage < 35 || matchedCount < 5) return "Low keyword overlap detected. Tailor your resume toward role technologies.";
        if (missingCount > matchedCount) return "Missing keyword pressure is high. Prioritize critical skills in project bullets.";
        return "JD alignment needs improvement. Strengthen targeted achievements and technology depth.";
    }

    private int completenessScore(ResumeIntelligenceProfile.ParsedResume parsed) {
        int sectionsPresent = 0;
        if (!parsed.getSkills().isEmpty()) sectionsPresent += 1;
        if (!parsed.getTechnologies().isEmpty()) sectionsPresent += 1;
        if (!parsed.getEducation().isEmpty()) sectionsPresent += 1;
        if (!parsed.getExperience().isEmpty()) sectionsPresent += 1;
        if (!parsed.getProjects().isEmpty()) sectionsPresent += 1;
        if (!parsed.getCertifications().isEmpty()) sectionsPresent += 1;
        if (!parsed.getCompanies().isEmpty()) sectionsPresent += 1;
        if (!parsed.getKeywords().isEmpty()) sectionsPresent += 1;
        return clamp((int) Math.round((sectionsPresent / 8.0) * 100));
    }

    private int keywordDiversityScore(String text) {
        List<String> tokens = tokenize(text);
        if (tokens.isEmpty()) return 0;
        int unique = new LinkedHashSet<>(tokens).size();
        double ratio = unique / (double) tokens.size();
        int score = (int) Math.round(ratio * 100);
        if (unique > 120) score += 10;
        if (unique > 180) score += 8;
        return clamp(score);
    }

    private int keywordStrengthScore(ResumeIntelligenceProfile.ParsedResume parsed, int diversity) {
        int raw = (parsed.getSkills().size() * 4) + (parsed.getTechnologies().size() * 3) + (parsed.getKeywords().size() * 2) + (parsed.getCompanies().size() * 2);
        int normalized = Math.min(100, raw);
        return clamp((int) Math.round((normalized * 0.68) + (diversity * 0.32)));
    }

    private int formattingScore(String text, int quantifiedSignals) {
        List<String> lines = Arrays.stream(normalizeText(text).split("\\r?\\n"))
                .map(this::normalizeText)
                .filter(item -> !item.isBlank())
                .toList();
        int bulletCount = (int) lines.stream().filter(line -> line.startsWith("-") || line.startsWith("*") || line.startsWith("•")).count();
        int bulletScore = Math.min(24, bulletCount * 3);
        int quantifiedScore = Math.min(28, quantifiedSignals * 6);
        int contactScore = (EMAIL_PATTERN.matcher(text).find() ? 8 : 0) + (PHONE_PATTERN.matcher(text).find() ? 8 : 0);
        int dateScore = DATE_PATTERN.matcher(text).find() ? 8 : 0;
        int lengthScore = lines.size() >= 18 ? 10 : (lines.size() >= 12 ? 6 : 2);
        return clamp(18 + bulletScore + quantifiedScore + contactScore + dateScore + lengthScore);
    }

    private int countQuantifiedSignals(String text) {
        Matcher matcher = QUANTIFIED_PATTERN.matcher(text == null ? "" : text);
        int count = 0;
        while (matcher.find()) count += 1;
        return count;
    }

    private List<String> formattingIndicators(String text, int quantifiedSignals, int diversityScore) {
        List<String> indicators = new ArrayList<>();
        indicators.add(EMAIL_PATTERN.matcher(text).find() ? "Contact email detected" : "Contact email missing");
        indicators.add(PHONE_PATTERN.matcher(text).find() ? "Phone number detected" : "Phone number missing");
        indicators.add(DATE_PATTERN.matcher(text).find() ? "Timeline markers detected" : "Date timeline is weak");
        indicators.add(quantifiedSignals < 3 ? "Add more quantified achievements" : "Quantified achievements present");
        if (diversityScore < 42) indicators.add("Low keyword diversity");
        return indicators;
    }

    private List<String> missingSkills(ResumeIntelligenceProfile.ParsedResume parsed, String roleContext) {
        Set<String> baseline = roleBaselineSkills(roleContext);
        Set<String> available = new LinkedHashSet<>();
        available.addAll(normalizeAll(parsed.getSkills()));
        available.addAll(normalizeAll(parsed.getTechnologies()));
        available.addAll(normalizeAll(parsed.getKeywords()));
        return baseline.stream().filter(skill -> !containsToken(available, skill)).limit(8).map(this::titleCase).toList();
    }

    private List<String> atsRecommendations(int ats,
                                            List<String> missingSkills,
                                            int quantifiedSignals,
                                            int diversity,
                                            List<Session> sessions,
                                            List<Feedback> feedback) {
        List<String> recommendations = new ArrayList<>();
        if (quantifiedSignals < 3) recommendations.add("Add more quantified achievements in experience and project bullets.");
        if (diversity < 42) recommendations.add("Low keyword diversity detected. Expand role-relevant terminology naturally.");
        if (!missingSkills.isEmpty()) recommendations.add("Missing skills: " + String.join(", ", missingSkills.subList(0, Math.min(4, missingSkills.size()))) + ".");
        if (ats < 65) recommendations.add("Strengthen section completeness with explicit education, projects, and certifications.");
        weakTopics(feedback).entrySet().stream().limit(2).forEach(entry -> recommendations.add("Focus on " + entry.getKey() + " interviews."));
        long completed = sessions.stream().filter(item -> "COMPLETED".equalsIgnoreCase(item.getStatus())).count();
        if (completed < 3) recommendations.add("Increase mock interview volume to improve readiness confidence.");
        if (recommendations.isEmpty()) recommendations.add("Resume is in strong shape. Keep refining high-impact bullets.");
        return recommendations.stream().distinct().limit(8).toList();
    }

    private int computeReadinessScore(int ats,
                                      List<Session> sessions,
                                      List<Feedback> feedback,
                                      ResumeIntelligenceProfile.ParsedResume parsed,
                                      ResumeIntelligenceProfile.AtsSummary atsSummary) {
        int completedSessions = (int) sessions.stream().filter(item -> "COMPLETED".equalsIgnoreCase(item.getStatus())).count();
        double avgFeedback = feedback.stream().mapToInt(Feedback::getRating).average().orElse(0.0);
        int weakPenalty = weakTopics(feedback).values().stream().mapToInt(Integer::intValue).sum();
        int skillDepth = Math.min(100, (parsed.getSkills().size() * 5) + (parsed.getTechnologies().size() * 4));
        int missingPenalty = atsSummary.getMissingSkills().size() * 4;
        int score = (int) Math.round(
                (ats * 0.45)
                        + (skillDepth * 0.2)
                        + (Math.min(100, completedSessions * 12) * 0.2)
                        + (Math.min(100, avgFeedback * 20) * 0.15)
                        - Math.min(24, weakPenalty)
                        - Math.min(18, missingPenalty)
        );
        return clamp(score);
    }

    private int baseReadinessWithoutResume(List<Session> sessions, List<Feedback> feedback) {
        int completedSessions = (int) sessions.stream().filter(item -> "COMPLETED".equalsIgnoreCase(item.getStatus())).count();
        double avgFeedback = feedback.stream().mapToInt(Feedback::getRating).average().orElse(0.0);
        return clamp((int) Math.round((completedSessions * 14) + (avgFeedback * 11) + 18));
    }

    private List<PrepDtos.ScorePoint> trendFromProgress(ResumeIntelligenceProfile profile, boolean atsTrend) {
        return profile.getProgressHistory().stream()
                .sorted(Comparator.comparing(ResumeIntelligenceProfile.ProgressSnapshot::getCapturedAt))
                .skip(Math.max(0, profile.getProgressHistory().size() - 10))
                .map(item -> new PrepDtos.ScorePoint(
                        SHORT_DATE.format(item.getCapturedAt() == null ? Instant.now() : item.getCapturedAt()),
                        atsTrend ? item.getAtsScore() : item.getReadinessScore(),
                        item.getCapturedAt()
                ))
                .toList();
    }

    private PrepDtos.ResumeVersionSummary toSummary(ResumeIntelligenceProfile.ResumeVersion version, boolean active) {
        ResumeIntelligenceProfile.JobMatchSnapshot latestMatch = latestMatch(version);
        return new PrepDtos.ResumeVersionSummary(
                version.getId(),
                version.getFileName(),
                version.getContentType(),
                version.getResumeUrl(),
                version.getUploadedAt(),
                active,
                version.getAtsSummary().getAtsScore(),
                version.getInterviewReadinessScore(),
                latestMatch == null ? null : latestMatch.getMatchPercent(),
                version.getRoleHint()
        );
    }

    private PrepDtos.ResumeParsedData toParsed(ResumeIntelligenceProfile.ParsedResume parsed) {
        return new PrepDtos.ResumeParsedData(
                parsed.getSkills(),
                parsed.getTechnologies(),
                parsed.getEducation(),
                parsed.getExperience(),
                parsed.getProjects(),
                parsed.getCertifications(),
                parsed.getCompanies(),
                parsed.getKeywords()
        );
    }

    private PrepDtos.AtsAnalysis toAts(ResumeIntelligenceProfile.AtsSummary ats) {
        return new PrepDtos.AtsAnalysis(
                ats.getAtsScore(),
                ats.getCompletenessScore(),
                ats.getKeywordStrength(),
                ats.getFormattingQualityScore(),
                ats.getQuantifiedAchievementSignals(),
                ats.getKeywordDiversityScore(),
                ats.getMissingSkills(),
                ats.getFormattingQualityIndicators(),
                ats.getRecommendations(),
                ats.getComponentScores()
        );
    }

    private PrepDtos.JobMatchResult toJobMatch(ResumeIntelligenceProfile.JobMatchSnapshot snapshot) {
        if (snapshot == null) return null;
        return new PrepDtos.JobMatchResult(
                snapshot.getMatchPercent(),
                snapshot.getKeywordCoverage(),
                snapshot.getMatchingStrengths(),
                snapshot.getMatchedKeywords(),
                snapshot.getMissingKeywords(),
                snapshot.getSummary(),
                snapshot.getEvaluatedAt()
        );
    }

    private ResumeIntelligenceProfile.ResumeVersion activeVersion(ResumeIntelligenceProfile profile) {
        if (profile.getActiveVersionId() == null || profile.getActiveVersionId().isBlank()) return null;
        return profile.getVersions().stream()
                .filter(item -> profile.getActiveVersionId().equals(item.getId()))
                .findFirst()
                .orElse(null);
    }

    private ResumeIntelligenceProfile.JobMatchSnapshot latestMatch(ResumeIntelligenceProfile.ResumeVersion version) {
        return version.getJobMatches().stream()
                .max(Comparator.comparing(ResumeIntelligenceProfile.JobMatchSnapshot::getEvaluatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
    }

    private void captureProgress(ResumeIntelligenceProfile profile, int atsScore, int readinessScore, int completedModules) {
        ResumeIntelligenceProfile.ProgressSnapshot snapshot = new ResumeIntelligenceProfile.ProgressSnapshot();
        snapshot.setCapturedAt(Instant.now());
        snapshot.setAtsScore(atsScore);
        snapshot.setReadinessScore(readinessScore);
        snapshot.setCompletedPrepModules(completedModules);

        List<ResumeIntelligenceProfile.ProgressSnapshot> history = new ArrayList<>(profile.getProgressHistory());
        history.add(snapshot);
        if (history.size() > MAX_PROGRESS_POINTS) {
            history = history.subList(history.size() - MAX_PROGRESS_POINTS, history.size());
        }
        profile.setProgressHistory(history);
    }

    private int completedModules(List<Session> sessions, List<Feedback> feedback) {
        int completedSessions = (int) sessions.stream().filter(item -> "COMPLETED".equalsIgnoreCase(item.getStatus())).count();
        return Math.max(0, (completedSessions * 2) + feedback.size());
    }

    private Map<String, Integer> skillGapMap(List<String> missingSkills) {
        if (missingSkills == null || missingSkills.isEmpty()) return Map.of("Core readiness", 82);
        Map<String, Integer> gaps = new LinkedHashMap<>();
        int severity = 78;
        for (String missing : missingSkills.stream().limit(8).toList()) {
            gaps.put(missing, severity);
            severity = Math.max(38, severity - 8);
        }
        return gaps;
    }

    private Map<String, Integer> topicReadinessMap(List<Session> sessions,
                                                   List<Feedback> feedback,
                                                   List<String> skills,
                                                   List<String> technologies) {
        Map<String, Integer> readiness = new LinkedHashMap<>();
        Map<String, Integer> weakMap = weakTopics(feedback);
        mergeReadiness(readiness, skills, 62);
        mergeReadiness(readiness, technologies, 58);
        sessions.forEach(session -> mergeReadiness(readiness, session.getTopics(), "COMPLETED".equalsIgnoreCase(session.getStatus()) ? 6 : 2));
        weakMap.forEach((topic, penalty) -> readiness.merge(topic, -Math.min(28, penalty), Integer::sum));
        return readiness.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        item -> clamp(item.getValue()),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private void mergeReadiness(Map<String, Integer> sink, Collection<String> values, int base) {
        if (values == null) return;
        for (String value : values) {
            String normalized = normalizeText(value).toLowerCase(Locale.ROOT);
            if (normalized.isBlank()) continue;
            sink.merge(titleCase(normalized), base, Integer::sum);
        }
    }

    private List<PrepDtos.RoadmapStep> roadmapFromContext(List<Session> sessions,
                                                          List<Feedback> feedback,
                                                          ResumeIntelligenceProfile.AtsSummary ats) {
        int completedSessions = (int) sessions.stream().filter(item -> "COMPLETED".equalsIgnoreCase(item.getStatus())).count();
        List<String> weak = weakAreas(feedback, ats.getMissingSkills());
        return List.of(
                new PrepDtos.RoadmapStep(1, "Optimize ATS baseline", "Refine summary, quantified bullets, and keywords for stronger screening.", "Easy", ats.getAtsScore() >= 70 ? "In progress" : "Priority", List.of("Quantified achievements", "Keyword balance", "Formatting checks")),
                new PrepDtos.RoadmapStep(2, "Role-specific skill gap closure", "Prioritize missing skills and map them to project proof points.", "Medium", ats.getMissingSkills().isEmpty() ? "On track" : "Priority", ats.getMissingSkills().isEmpty() ? List.of("Advanced depth", "Role storytelling") : ats.getMissingSkills().stream().limit(4).toList()),
                new PrepDtos.RoadmapStep(3, "Targeted mock loops", "Run practice loops for weak topics from medium to hard difficulty.", "Medium", completedSessions >= 4 ? "In progress" : "Queued", weak.isEmpty() ? List.of("System design", "Behavioral", "Coding clarity") : weak.stream().limit(4).toList()),
                new PrepDtos.RoadmapStep(4, "Company and role sprint", "Finalize company-specific rounds with timed drills and behavioral polish.", "Hard", completedSessions >= 8 ? "Ready" : "Queued", List.of("Google SDE Prep", "Amazon Behavioral Prep", "React Frontend Prep", "System Design Prep"))
        );
    }

    private List<PrepDtos.RoadmapStep> defaultRoadmap(String context) {
        return List.of(
                new PrepDtos.RoadmapStep(1, "Upload and parse resume", "Upload a PDF or DOCX to unlock ATS and JD matching.", "Easy", "Priority", List.of(context)),
                new PrepDtos.RoadmapStep(2, "Run ATS analysis", "Review score components and fix missing sections.", "Easy", "Queued", List.of("Completeness", "Keyword diversity")),
                new PrepDtos.RoadmapStep(3, "Run JD matching", "Paste a target role JD and close missing keyword gaps.", "Medium", "Queued", List.of("Role keywords", "Technology alignment")),
                new PrepDtos.RoadmapStep(4, "Start prep loops", "Practice weak topics and measure readiness trends over time.", "Medium", "Queued", List.of("Coding", "Behavioral", "System design"))
        );
    }

    private List<String> weakAreas(List<Feedback> feedback, List<String> missingSkills) {
        List<String> weak = new ArrayList<>();
        weak.addAll(weakTopics(feedback).keySet().stream().limit(4).toList());
        if (missingSkills != null) weak.addAll(missingSkills.stream().limit(4).toList());
        return weak.stream().map(this::titleCase).distinct().limit(8).toList();
    }

    private List<String> recommendedTopics(List<Session> sessions,
                                           List<Feedback> feedback,
                                           List<String> skills,
                                           List<String> missingSkills) {
        LinkedHashSet<String> topics = new LinkedHashSet<>();
        topics.addAll(weakAreas(feedback, missingSkills));
        sessions.stream().flatMap(session -> session.getTopics().stream()).limit(6).map(this::titleCase).forEach(topics::add);
        skills.stream().limit(4).map(this::titleCase).forEach(topics::add);
        topics.add("Google SDE Prep");
        topics.add("Amazon Behavioral Prep");
        topics.add("React Frontend Prep");
        topics.add("System Design Prep");
        return topics.stream().limit(12).toList();
    }

    private List<PrepDtos.RecommendationCard> buildRecommendationCards(ResumeIntelligenceProfile.ResumeVersion active,
                                                                       List<Session> sessions,
                                                                       List<Feedback> feedback,
                                                                       PrepDtos.JobMatchResult match) {
        ResumeIntelligenceProfile.AtsSummary ats = active.getAtsSummary();
        List<PrepDtos.RecommendationCard> cards = new ArrayList<>();
        cards.add(new PrepDtos.RecommendationCard("ATS optimization", "Improve ATS score by strengthening measurable impact and section completeness.", ats.getAtsScore() < 60 ? "High" : "Medium", ats.getRecommendations().stream().limit(3).toList()));
        List<String> weak = weakAreas(feedback, ats.getMissingSkills());
        cards.add(new PrepDtos.RecommendationCard("Weak-topic practice", "Run focused drills on weak interview areas and track score recovery.", weak.isEmpty() ? "Medium" : "High", weak.isEmpty() ? List.of("Behavioral structure", "System design communication") : weak.stream().limit(4).toList()));
        if (match != null) {
            cards.add(new PrepDtos.RecommendationCard("JD alignment sprint", "Close critical role-match gaps before applications and interviews.", match.matchPercent() < 60 ? "High" : "Medium", match.missingKeywords().isEmpty() ? List.of("Maintain keyword coverage") : match.missingKeywords().stream().limit(4).toList()));
        }
        int completedSessions = (int) sessions.stream().filter(item -> "COMPLETED".equalsIgnoreCase(item.getStatus())).count();
        cards.add(new PrepDtos.RecommendationCard("Interview cadence", "Keep a steady practice rhythm to improve readiness confidence over time.", completedSessions < 4 ? "High" : "Medium", List.of("2 role-focused mocks weekly", "1 behavioral review", "1 systems discussion")));
        return cards.stream().limit(4).toList();
    }

    private List<PrepDtos.RecommendationCard> recommendationCardsWithoutResume(List<Session> sessions, List<Feedback> feedback) {
        int completed = (int) sessions.stream().filter(item -> "COMPLETED".equalsIgnoreCase(item.getStatus())).count();
        return List.of(
                new PrepDtos.RecommendationCard("Upload resume", "Resume intelligence is locked until a PDF or DOCX resume is uploaded.", "High", List.of("Upload latest resume", "Run ATS analysis", "Start JD matching")),
                new PrepDtos.RecommendationCard("Practice consistency", "Maintain interview momentum while resume analysis is being prepared.", completed < 3 ? "High" : "Medium", List.of("Schedule coding practice", "Run behavioral mock")),
                new PrepDtos.RecommendationCard("Feedback loop", "Use previous feedback to shape weekly prep goals.", "Medium", weakAreas(feedback, List.of()).stream().limit(4).toList())
        );
    }

    private Map<String, Integer> weakTopics(List<Feedback> feedback) {
        Map<String, Integer> weak = new LinkedHashMap<>();
        for (Feedback item : feedback) {
            if (item == null) continue;
            for (Feedback.TopicFeedback topic : item.getTopicFeedback()) {
                if (topic == null) continue;
                String key = titleCase(topic.getTopic());
                int rating = topic.getRating() == null ? 0 : topic.getRating();
                if (!key.isBlank() && rating > 0 && rating <= 3) weak.merge(key, (4 - rating) * 3, Integer::sum);
                topic.getSkillRatings().forEach((skill, score) -> {
                    if (score == null || score > 3) return;
                    String normalized = titleCase(skill);
                    if (!normalized.isBlank()) weak.merge(normalized, (4 - score) * 2, Integer::sum);
                });
            }
        }
        return weak.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private Set<String> roleBaselineSkills(String roleContext) {
        String role = normalizeText(roleContext).toLowerCase(Locale.ROOT);
        if (role.contains("backend") || role.contains("java") || role.contains("api")) {
            return new LinkedHashSet<>(List.of("java", "spring boot", "microservices", "sql", "rest api", "docker", "system design"));
        }
        if (role.contains("frontend") || role.contains("react") || role.contains("ui")) {
            return new LinkedHashSet<>(List.of("react", "javascript", "typescript", "html", "css", "api integration", "testing"));
        }
        if (role.contains("full stack") || role.contains("fullstack")) {
            return new LinkedHashSet<>(List.of("react", "node.js", "sql", "rest api", "docker", "system design", "cloud"));
        }
        if (role.contains("data")) {
            return new LinkedHashSet<>(List.of("python", "sql", "statistics", "machine learning", "data structures", "communication"));
        }
        return new LinkedHashSet<>(List.of("data structures", "algorithms", "system design", "behavioral", "communication", "problem solving"));
    }

    private String roleContext(User user, String overrideRoleHint) {
        if (overrideRoleHint != null && !overrideRoleHint.isBlank()) return overrideRoleHint;
        if (user.getCurrentRole() != null && !user.getCurrentRole().isBlank()) return user.getCurrentRole();
        if (user.getPreferredDomains() != null && !user.getPreferredDomains().isEmpty()) return String.join(" ", user.getPreferredDomains());
        return "general software engineer";
    }

    private List<String> detectKeywordsFromLexicon(String text, Set<String> lexicon) {
        String normalized = " " + normalizeText(text).toLowerCase(Locale.ROOT) + " ";
        List<String> found = new ArrayList<>();
        for (String item : lexicon) {
            if (containsToken(normalized, item.toLowerCase(Locale.ROOT))) found.add(titleCase(item));
        }
        return found;
    }

    private List<String> splitLooseItems(List<String> lines) {
        if (lines == null || lines.isEmpty()) return List.of();
        List<String> values = new ArrayList<>();
        for (String line : lines) {
            String cleaned = normalizeText(line.replace('|', ',').replace(';', ','));
            for (String part : cleaned.split(",")) {
                String value = normalizeText(part);
                if (value.length() < 2 || value.length() > 40) continue;
                if (value.matches("(?i)^(skills?|experience|project|education|certification)$")) continue;
                values.add(titleCase(value));
            }
        }
        return values;
    }

    private List<String> filterByPattern(List<String> values, Pattern pattern, int limit) {
        if (values == null) return List.of();
        return values.stream()
                .map(this::normalizeText)
                .filter(item -> !item.isBlank())
                .filter(item -> pattern.matcher(item).find())
                .distinct()
                .limit(limit)
                .toList();
    }

    private List<String> trimList(List<String> values, int limit) {
        if (values == null) return List.of();
        return values.stream()
                .map(this::normalizeText)
                .filter(item -> !item.isBlank())
                .distinct()
                .limit(limit)
                .toList();
    }

    private List<String> coalesceSection(Map<String, List<String>> sections, String key) {
        List<String> direct = sections.getOrDefault(key, List.of());
        if (!direct.isEmpty()) return direct;
        return sections.getOrDefault("general", List.of());
    }

    private List<String> extractCompanies(String text, List<String> experience, List<String> projects) {
        Set<String> companies = new LinkedHashSet<>();
        Pattern pattern = Pattern.compile("(?i)(?:at|for|with)\\s+([A-Z][A-Za-z0-9&.\\-]{1,30}(?:\\s+[A-Z][A-Za-z0-9&.\\-]{1,30}){0,2})");
        Matcher matcher = pattern.matcher(text == null ? "" : text);
        while (matcher.find()) {
            String company = normalizeText(matcher.group(1));
            if (company.length() >= 2 && company.length() <= 45) companies.add(company);
        }
        List<String> combined = new ArrayList<>();
        if (experience != null) combined.addAll(experience);
        if (projects != null) combined.addAll(projects);
        for (String line : combined) {
            Matcher local = pattern.matcher(line);
            while (local.find()) {
                String company = normalizeText(local.group(1));
                if (company.length() >= 2 && company.length() <= 45) companies.add(company);
            }
        }
        return companies.stream().limit(12).toList();
    }

    private List<String> extractTopKeywords(String text, int limit) {
        List<String> tokens = tokenize(text).stream()
                .filter(token -> token.length() >= 3)
                .filter(token -> !STOP_WORDS.contains(token))
                .toList();
        Map<String, Integer> frequency = new HashMap<>();
        for (String token : tokens) frequency.merge(token, 1, Integer::sum);
        return frequency.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry::getKey))
                .limit(limit)
                .map(item -> titleCase(item.getKey()))
                .toList();
    }

    private List<String> tokenize(String text) {
        return Arrays.stream(normalizeText(text).toLowerCase(Locale.ROOT).split("[^a-z0-9+#.]+"))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private List<String> mergeUnique(List<String> left, List<String> right) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (left != null) left.stream().map(this::titleCase).filter(item -> !item.isBlank()).forEach(merged::add);
        if (right != null) right.stream().map(this::titleCase).filter(item -> !item.isBlank()).forEach(merged::add);
        return merged.stream().limit(30).toList();
    }

    private Set<String> normalizeAll(Collection<String> values) {
        if (values == null) return Set.of();
        return values.stream()
                .map(this::normalizeText)
                .map(item -> item.toLowerCase(Locale.ROOT))
                .filter(item -> !item.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean containsToken(Set<String> values, String target) {
        String normalized = normalizeText(target).toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) return false;
        return values.stream().anyMatch(value -> containsToken(value, normalized));
    }

    private boolean containsToken(String source, String target) {
        String normalizedSource = " " + normalizeText(source).toLowerCase(Locale.ROOT) + " ";
        String normalizedTarget = " " + normalizeText(target).toLowerCase(Locale.ROOT) + " ";
        if (normalizedTarget.trim().length() <= 3) {
            return normalizedSource.matches(".*[^a-z0-9]" + Pattern.quote(normalizedTarget.trim()) + "[^a-z0-9].*");
        }
        return normalizedSource.contains(normalizedTarget);
    }

    private String normalizeContentType(String contentType, String fileName) {
        if (contentType != null && !contentType.isBlank()) return contentType.trim().toLowerCase(Locale.ROOT);
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (lower.endsWith(".txt") || lower.endsWith(".md")) return "text/plain";
        return "application/octet-stream";
    }

    private boolean isPdf(String contentType, String fileName) {
        return "application/pdf".equalsIgnoreCase(contentType) || (fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".pdf"));
    }

    private boolean isDocx(String contentType, String fileName) {
        return "application/vnd.openxmlformats-officedocument.wordprocessingml.document".equalsIgnoreCase(contentType)
                || (fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".docx"));
    }

    private boolean isText(String contentType, String fileName) {
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("text/")) return true;
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".txt") || lower.endsWith(".md");
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private String normalizeText(String value) {
        if (value == null) return "";
        return value.replace('\u0000', ' ').replaceAll("\\s+", " ").trim();
    }

    private String sanitizeDocumentText(String value) {
        if (value == null) return "";
        return value.replace('\u0000', ' ')
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\t\\x0B\\f]+", " ")
                .replaceAll(" +", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private String titleCase(String value) {
        String normalized = normalizeText(value);
        if (normalized.isBlank()) return "";
        if (normalized.length() <= 4 && normalized.equals(normalized.toUpperCase(Locale.ROOT))) return normalized;
        return Arrays.stream(normalized.split(" "))
                .map(word -> {
                    if (word.isBlank()) return "";
                    if (word.length() <= 3 && word.equals(word.toUpperCase(Locale.ROOT))) return word;
                    return Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase(Locale.ROOT);
                })
                .collect(Collectors.joining(" "));
    }

    private String trimToNull(String value) {
        String normalized = normalizeText(value);
        return normalized.isBlank() ? null : normalized;
    }
}
