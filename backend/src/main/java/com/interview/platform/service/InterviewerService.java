package com.interview.platform.service;

import com.interview.platform.dto.PageResponse;
import com.interview.platform.dto.AvailabilityDtos;
import com.interview.platform.dto.InterviewerFilterOptions;
import com.interview.platform.dto.MarketplaceDtos;
import com.interview.platform.exception.ResourceNotFoundException;
import com.interview.platform.config.CacheConfig;
import com.interview.platform.model.Feedback;
import com.interview.platform.model.User;
import com.interview.platform.repository.FeedbackRepository;
import com.interview.platform.repository.SessionRepository;
import com.interview.platform.repository.UserRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class InterviewerService {
    private static final int SEARCH_WINDOW_SIZE = 120;
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-z0-9._-]{3,24}$");

    private final MongoTemplate mongoTemplate;
    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final FeedbackRepository feedbackRepository;
    private final AvailabilitySlotService availabilitySlotService;

    public InterviewerService(MongoTemplate mongoTemplate,
                              UserRepository userRepository,
                              SessionRepository sessionRepository,
                              FeedbackRepository feedbackRepository,
                              AvailabilitySlotService availabilitySlotService) {
        this.mongoTemplate = mongoTemplate;
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.feedbackRepository = feedbackRepository;
        this.availabilitySlotService = availabilitySlotService;
    }

    public PageResponse<MarketplaceDtos.InterviewerCard> search(String q, String expertise, String company, String role, Integer minExperience,
                                                                Integer maxExperience, Double minRating, Boolean available, Boolean free, String language,
                                                                String experienceLevel, Boolean verified, String timezone, String topic,
                                                                Boolean availableToday, Integer sessionDuration, String viewerTimezone, Boolean publicOnly, String excludeUserId,
                                                                String sort, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 24));
        Query query = new Query();
        List<Criteria> criteria = new ArrayList<>();
        criteria.add(new Criteria().orOperator(Criteria.where("roles").is("INTERVIEWER"), Criteria.where("role").is("INTERVIEWER")));
        criteria.add(Criteria.where("accountEnabled").ne(false));
        criteria.add(Criteria.where("publicProfileVisible").ne(false));
        if (!isBlank(excludeUserId)) criteria.add(Criteria.where("id").ne(excludeUserId.trim()));
        if (!isBlank(q)) {
            Pattern pattern = Pattern.compile(Pattern.quote(q.trim()), Pattern.CASE_INSENSITIVE);
            criteria.add(new Criteria().orOperator(
                    Criteria.where("username").regex(pattern),
                    Criteria.where("displayName").regex(pattern),
                    Criteria.where("company").regex(pattern),
                    Criteria.where("currentRole").regex(pattern),
                    Criteria.where("bio").regex(pattern),
                    Criteria.where("skills").regex(pattern),
                    Criteria.where("interviewTopics").regex(pattern)
            ));
        }
        if (!isBlank(expertise)) addAnyFieldContains(criteria, List.of("preferredDomains", "skills"), splitOptions(expertise));
        if (!isBlank(company)) addAnyFieldContains(criteria, List.of("company"), splitOptions(company));
        if (!isBlank(role)) criteria.add(Criteria.where("currentRole").regex(Pattern.compile(Pattern.quote(role.trim()), Pattern.CASE_INSENSITIVE)));
        if (!isBlank(language)) addAnyFieldContains(criteria, List.of("language"), splitOptions(language));
        if (!isBlank(timezone)) addAnyFieldContains(criteria, List.of("timeZone"), splitOptions(timezone));
        if (!isBlank(topic)) {
            addAnyFieldContains(criteria, List.of("interviewTopics", "skills", "preferredDomains"), splitOptions(topic));
        }
        if (minExperience != null) criteria.add(Criteria.where("yearsExperience").gte(minExperience));
        if (maxExperience != null) criteria.add(Criteria.where("yearsExperience").lte(maxExperience));
        if (minRating != null) criteria.add(Criteria.where("averageRating").gte(minRating));
        if (Boolean.TRUE.equals(available)) criteria.add(Criteria.where("acceptingBookings").is(true));
        if (Boolean.TRUE.equals(free)) criteria.add(new Criteria().orOperator(Criteria.where("priceCents").is(0), Criteria.where("priceCents").exists(false)));
        if (!isBlank(experienceLevel)) criteria.add(Criteria.where("experienceLevel").regex(Pattern.compile(Pattern.quote(experienceLevel.trim()), Pattern.CASE_INSENSITIVE)));
        if (Boolean.TRUE.equals(verified)) criteria.add(Criteria.where("interviewerVerified").is(true));
        query.addCriteria(new Criteria().andOperator(criteria.toArray(new Criteria[0])));

        List<User> filtered = mongoTemplate.find(query, User.class).stream()
                .filter(user -> matchesDynamicFilters(user, availableToday, sessionDuration))
                .sorted(sortComparator(sort, viewerTimezone))
                .toList();
        int fromIndex = Math.min(safePage * safeSize, filtered.size());
        int toIndex = Math.min(fromIndex + safeSize, filtered.size());
        return new PageResponse<>(
                filtered.subList(fromIndex, toIndex).stream().map(this::toInterviewerCard).toList(),
                filtered.size(),
                safePage,
                safeSize
        );
    }

    @Cacheable(cacheNames = CacheConfig.INTERVIEWER_CARD_CACHE, key = "#id")
    public MarketplaceDtos.InterviewerCard getById(String id) {
        User user = userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Interviewer not found"));
        if (!user.hasRole("INTERVIEWER")) {
            throw new ResourceNotFoundException("Interviewer not found");
        }
        return toInterviewerCard(user);
    }

    public List<MarketplaceDtos.InterviewerCard> topRated(String excludeUserId) {
        return topRated(excludeUserId, false);
    }

    @Cacheable(cacheNames = CacheConfig.INTERVIEWER_TOP_RATED_CACHE, key = "(#excludeUserId == null ? '' : #excludeUserId) + ':' + (#publicOnly == null ? false : #publicOnly)")
    public List<MarketplaceDtos.InterviewerCard> topRated(String excludeUserId, Boolean publicOnly) {
        return userRepository.findByRoleOrderByAverageRatingDesc("INTERVIEWER").stream()
                .filter(user -> !Boolean.FALSE.equals(user.getAccountEnabled()))
                .filter(this::isPubliclyVisible)
                .filter(user -> isBlank(excludeUserId) || !excludeUserId.equals(user.getId()))
                .limit(6)
                .map(this::toInterviewerCard)
                .toList();
    }

    @Cacheable(cacheNames = CacheConfig.INTERVIEWER_RECOMMENDED_CACHE, key = "#intervieweeId == null ? 'anonymous' : #intervieweeId")
    public List<MarketplaceDtos.InterviewerCard> recommended(String intervieweeId) {
        List<User> candidates = userRepository.findByRoleAndAcceptingBookingsOrderByCompletedInterviewsDesc("INTERVIEWER", true).stream()
                .filter(user -> !Boolean.FALSE.equals(user.getAccountEnabled()))
                .filter(this::isPubliclyVisible)
                .filter(user -> isBlank(intervieweeId) || !intervieweeId.equals(user.getId()))
                .limit(SEARCH_WINDOW_SIZE)
                .toList();
        if (isBlank(intervieweeId)) {
            return candidates.stream().limit(8).map(this::toInterviewerCard).toList();
        }
        User interviewee = userRepository.findById(intervieweeId).orElse(null);
        if (interviewee == null) {
            return candidates.stream().limit(8).map(this::toInterviewerCard).toList();
        }
        Set<String> historyTopics = interviewHistoryTopics(intervieweeId);
        return candidates.stream()
                .sorted(Comparator.comparingDouble(user -> -recommendationScore(interviewee, historyTopics, user)))
                .limit(8)
                .map(this::toInterviewerCard)
                .toList();
    }

    @Cacheable(cacheNames = CacheConfig.INTERVIEWER_AUTOCOMPLETE_CACHE, key = "#q == null ? '' : #q.trim().toLowerCase()")
    public List<MarketplaceDtos.SearchSuggestion> autocomplete(String q) {
        String query = normalizeOption(q).toLowerCase(Locale.ROOT);
        if (query.isBlank()) {
            return List.of();
        }
        List<MarketplaceDtos.SearchSuggestion> suggestions = new ArrayList<>();
        filterOptions().getExpertise().stream()
                .filter(item -> item.toLowerCase(Locale.ROOT).contains(query))
                .limit(4)
                .forEach(item -> suggestions.add(new MarketplaceDtos.SearchSuggestion(item, "expertise")));
        filterOptions().getCompanies().stream()
                .filter(item -> item.toLowerCase(Locale.ROOT).contains(query))
                .limit(3)
                .forEach(item -> suggestions.add(new MarketplaceDtos.SearchSuggestion(item, "company")));
        filterOptions().getTopics().stream()
                .filter(item -> item.toLowerCase(Locale.ROOT).contains(query))
                .limit(4)
                .forEach(item -> suggestions.add(new MarketplaceDtos.SearchSuggestion(item, "topic")));
        userRepository.findByRole("INTERVIEWER").stream()
                .filter(user -> !Boolean.FALSE.equals(user.getAccountEnabled()))
                .filter(this::isPubliclyVisible)
                .map(this::interviewerIdentity)
                .filter(item -> item.toLowerCase(Locale.ROOT).contains(query))
                .distinct()
                .limit(4)
                .forEach(item -> suggestions.add(new MarketplaceDtos.SearchSuggestion(item, "interviewer")));
        return suggestions.stream().distinct().limit(12).toList();
    }

    @Cacheable(cacheNames = CacheConfig.INTERVIEWER_PUBLIC_PROFILE_CACHE, key = "#username == null ? '' : #username.trim().toLowerCase()")
    public MarketplaceDtos.PublicInterviewerProfile publicProfile(String username) {
        String normalized = normalizeOption(username).toLowerCase(Locale.ROOT);
        if (normalized.isBlank() || !USERNAME_PATTERN.matcher(normalized).matches()) {
            throw new ResourceNotFoundException("Interviewer not found");
        }
        User interviewer = userRepository.findByUsernameKey(normalized)
                .or(() -> userRepository.findFirstByUsernamePattern("^" + Pattern.quote(normalized) + "$"))
                .filter(this::hasRole)
                .orElseThrow(() -> new ResourceNotFoundException("Interviewer not found"));
        if (!interviewer.hasRole("INTERVIEWER") || Boolean.FALSE.equals(interviewer.getPublicProfileVisible()) || Boolean.FALSE.equals(interviewer.getAccountEnabled())) {
            throw new ResourceNotFoundException("Interviewer not found");
        }
        List<String> availabilityPreview = availabilitySlotService.availableSlotStartTimes(interviewer.getId(), 7).stream()
                .limit(6)
                .toList();
        List<MarketplaceDtos.PublicReview> reviews = feedbackRepository.findByInterviewerIdAndPublicReviewTrueOrderByCreatedAtDesc(interviewer.getId()).stream()
                .limit(6)
                .map(this::toPublicReview)
                .toList();
        return new MarketplaceDtos.PublicInterviewerProfile(
                interviewer.getId(),
                interviewer.getUsername(),
                interviewer.getDisplayName(),
                interviewer.getAvatarUrl(),
                interviewer.getCompany(),
                interviewer.getCurrentRole(),
                interviewer.getBio(),
                interviewer.getLanguage(),
                interviewer.getTimeZone(),
                interviewer.getSkills(),
                interviewer.getInterviewTopics(),
                interviewer.getPreferredDomains(),
                interviewer.getSessionDurations(),
                interviewer.getExperienceLevel(),
                interviewer.getYearsExperience(),
                interviewer.getAverageRating(),
                interviewer.getReviewCount(),
                interviewer.getCompletedInterviews(),
                interviewer.getCompletedSessions(),
                interviewer.getCancelledSessions(),
                reliabilityScore(interviewer),
                interviewer.getInterviewerVerified(),
                interviewer.getAcceptingBookings(),
                availabilityPreview,
                reviews
        );
    }

    private double recommendationScore(User interviewee, Set<String> historyTopics, User interviewer) {
        double rating = interviewer.getAverageRating() == null ? 0.0 : interviewer.getAverageRating();
        int completed = interviewer.getCompletedInterviews() == null ? 0 : interviewer.getCompletedInterviews();
        int years = interviewer.getYearsExperience() == null ? 0 : interviewer.getYearsExperience();
        int overlap = preferenceOverlap(interviewee, interviewer);
        int topicCompatibility = topicCompatibility(interviewee, historyTopics, interviewer);
        double languageBonus = languageMatchScore(interviewee, interviewer);
        double timezoneBonus = timezoneMatchScore(interviewee, interviewer);
        double availabilityBonus = availabilityOverlapScore(interviewee, interviewer);
        double verificationBonus = Boolean.TRUE.equals(interviewer.getInterviewerVerified()) ? 2.0 : 0.0;
        return (overlap * 5.5)
                + (topicCompatibility * 3.5)
                + languageBonus
                + timezoneBonus
                + availabilityBonus
                + verificationBonus
                + (rating * 2.0)
                + (Math.min(30, completed) * 0.15)
                + (Math.min(15, years) * 0.2);
    }

    private int preferenceOverlap(User interviewee, User interviewer) {
        List<String> preferred = interviewee.getPreferredDomains();
        List<String> skills = combinedInterviewerTopics(interviewer);
        if (preferred == null || preferred.isEmpty() || skills == null || skills.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (String pref : preferred) {
            if (isBlank(pref)) continue;
            String needle = pref.toLowerCase(Locale.ROOT);
            boolean matched = skills.stream().anyMatch(skill -> skill != null && skill.toLowerCase(Locale.ROOT).contains(needle));
            if (matched) count += 1;
        }
        return count;
    }

    public List<String> availableSlots(String interviewerId, Integer days) {
        getById(interviewerId);
        return availabilitySlotService.availableSlotStartTimes(interviewerId, days);
    }

    public List<AvailabilityDtos.GeneratedSlotResponse> generatedSlots(String interviewerId, Integer days) {
        return generatedSlots(interviewerId, days, false);
    }

    public List<AvailabilityDtos.GeneratedSlotResponse> generatedSlots(String interviewerId, Integer days, boolean includeUnavailable) {
        getById(interviewerId);
        return availabilitySlotService.generatedSlotResponses(interviewerId, days, includeUnavailable);
    }

    public InterviewerFilterOptions filterOptions() {
        return filterOptions(false);
    }

    @Cacheable(cacheNames = CacheConfig.INTERVIEWER_FILTER_OPTIONS_CACHE, key = "#publicOnly == null ? false : #publicOnly")
    public InterviewerFilterOptions filterOptions(Boolean publicOnly) {
        Query query = new Query(interviewerCriteria(publicOnly));
        query.fields()
                .include("skills")
                .include("language")
                .include("company")
                .include("experienceLevel")
                .include("timeZone")
                .include("preferredDomains")
                .include("interviewTopics")
                .include("sessionDurations");
        List<User> interviewers = mongoTemplate.find(query, User.class);
        List<String> domains = uniqueNormalized(interviewers.stream().flatMap(user -> splitOptions(user.getPreferredDomains()).stream()).toList());
        return new InterviewerFilterOptions(
                domains.isEmpty()
                        ? uniqueNormalized(interviewers.stream().flatMap(user -> splitOptions(user.getSkills()).stream()).toList())
                        : domains,
                uniqueNormalized(interviewers.stream().flatMap(user -> splitOptions(user.getLanguage()).stream()).toList()),
                uniqueNormalized(interviewers.stream().map(User::getCompany).toList()),
                uniqueNormalized(interviewers.stream().map(User::getExperienceLevel).toList()),
                uniqueNormalized(interviewers.stream().map(User::getTimeZone).toList()),
                uniqueNormalized(interviewers.stream()
                        .flatMap(user -> {
                            List<String> topics = new ArrayList<>();
                            topics.addAll(splitOptions(user.getInterviewTopics()));
                            topics.addAll(splitOptions(user.getSkills()));
                            return topics.stream();
                        })
                        .toList()),
                uniqueIntegers(interviewers.stream().flatMap(user -> user.getSessionDurations().stream()).toList())
        );
    }

    @Cacheable(cacheNames = CacheConfig.INTERVIEWER_PUBLIC_SUMMARY_CACHE, key = "'summary'")
    public MarketplaceDtos.PublicMarketplaceSummary publicMarketplaceSummary() {
        List<User> interviewers = mongoTemplate.find(new Query(interviewerCriteria(true)), User.class);
        long interviewerCount = interviewers.size();
        long verifiedCount = interviewers.stream().filter(user -> Boolean.TRUE.equals(user.getInterviewerVerified())).count();
        long availableCount = interviewers.stream().filter(user -> Boolean.TRUE.equals(user.getAcceptingBookings())).count();
        long availableTodayCount = interviewers.stream()
                .filter(user -> Boolean.TRUE.equals(user.getAcceptingBookings()))
                .filter(user -> hasMatchingSlot(user, 1, null))
                .count();
        long reviewCount = interviewers.stream().mapToLong(user -> user.getReviewCount() == null ? 0 : user.getReviewCount()).sum();
        long completedSessions = interviewers.stream().mapToLong(user -> user.getCompletedSessions() == null ? 0 : user.getCompletedSessions()).sum();
        List<MarketplaceDtos.InterviewerCard> featured = interviewers.stream()
                .sorted(defaultMarketplaceComparator(""))
                .sorted(Comparator.comparing(User::getAverageRating, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(4)
                .map(this::toInterviewerCard)
                .toList();
        return new MarketplaceDtos.PublicMarketplaceSummary(
                interviewerCount,
                verifiedCount,
                availableCount,
                availableTodayCount,
                reviewCount,
                completedSessions,
                featured
        );
    }

    public User toggleFavorite(String userId, String interviewerId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        userRepository.findById(interviewerId)
                .filter(this::hasRole)
                .orElseThrow(() -> new ResourceNotFoundException("Interviewer not found"));
        List<String> favorites = new ArrayList<>(user.getFavoriteInterviewerIds());
        if (favorites.contains(interviewerId)) {
            favorites.remove(interviewerId);
        } else {
            favorites.add(interviewerId);
        }
        user.setFavoriteInterviewerIds(favorites);
        return userRepository.save(user);
    }

    private Comparator<User> sortComparator(String sort, String viewerTimezone) {
        String normalized = sort == null ? "top-rated" : sort.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "most-experienced" -> Comparator.comparing(User::getYearsExperience, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(defaultMarketplaceComparator(viewerTimezone));
            case "most-active" -> Comparator.comparing(User::getCompletedInterviews, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(defaultMarketplaceComparator(viewerTimezone));
            case "newest" -> Comparator.comparing(User::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(defaultMarketplaceComparator(viewerTimezone));
            default -> defaultMarketplaceComparator(viewerTimezone);
        };
    }

    private Comparator<User> defaultMarketplaceComparator(String viewerTimezone) {
        Map<String, Integer> availabilityScoreCache = new HashMap<>();
        return Comparator
                .comparingInt((User user) -> timezoneAffinityScore(user, viewerTimezone)).reversed()
                .thenComparing(Comparator.comparingInt((User user) -> verificationScore(user)).reversed())
                .thenComparing(Comparator.comparingInt((User user) -> bookingAvailabilityScore(user, availabilityScoreCache)).reversed())
                .thenComparing(User::getAverageRating, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(User::getCompletedInterviews, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(User::getYearsExperience, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(User::getReviewCount, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private Criteria interviewerCriteria(Boolean publicOnly) {
        List<Criteria> criteria = new ArrayList<>();
        criteria.add(new Criteria().orOperator(Criteria.where("roles").is("INTERVIEWER"), Criteria.where("role").is("INTERVIEWER")));
        criteria.add(Criteria.where("accountEnabled").ne(false));
        criteria.add(Criteria.where("publicProfileVisible").ne(false));
        return new Criteria().andOperator(criteria.toArray(new Criteria[0]));
    }

    private Criteria interviewerCriteria() {
        return interviewerCriteria(false);
    }

    private void addAnyFieldContains(List<Criteria> criteria, List<String> fields, List<String> values) {
        List<Criteria> options = new ArrayList<>();
        values.stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .forEach(value -> {
                    Pattern pattern = Pattern.compile(Pattern.quote(value), Pattern.CASE_INSENSITIVE);
                    fields.forEach(field -> options.add(Criteria.where(field).regex(pattern)));
                });
        if (options.isEmpty()) {
            return;
        }
        if (options.size() == 1) {
            criteria.add(options.get(0));
        } else {
            criteria.add(new Criteria().orOperator(options.toArray(new Criteria[0])));
        }
    }

    private List<String> splitOptions(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .flatMap(value -> splitOptions(value).stream())
                .toList();
    }

    private List<String> splitOptions(String value) {
        if (isBlank(value)) {
            return List.of();
        }
        return Pattern.compile(",").splitAsStream(value)
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private List<String> uniqueNormalized(List<String> values) {
        Map<String, String> options = new LinkedHashMap<>();
        values.stream()
                .map(this::normalizeOption)
                .filter(value -> !value.isBlank())
                .forEach(value -> options.putIfAbsent(value.toLowerCase(Locale.ROOT), value));
        return options.values().stream()
                .sorted(Comparator.comparing(value -> value.toLowerCase(Locale.ROOT)))
                .toList();
    }

    private List<Integer> uniqueIntegers(List<Integer> values) {
        return values.stream()
                .filter(value -> value != null && value > 0)
                .distinct()
                .sorted()
                .toList();
    }

    private boolean matchesDynamicFilters(User user, Boolean availableToday, Integer sessionDuration) {
        if (Boolean.TRUE.equals(availableToday) && !hasMatchingSlot(user, 1, sessionDuration)) {
            return false;
        }
        if (sessionDuration != null && !supportsDuration(user, sessionDuration) && !hasMatchingSlot(user, 14, sessionDuration)) {
            return false;
        }
        return true;
    }

    private boolean hasMatchingSlot(User user, int days, Integer sessionDuration) {
        return availabilitySlotService.generatedSlotResponses(user.getId(), days, false).stream()
                .anyMatch(slot -> sessionDuration == null || sessionDuration.equals(slot.getDurationMinutes()));
    }

    private boolean supportsDuration(User user, Integer sessionDuration) {
        return sessionDuration == null || user.getSessionDurations().isEmpty() || user.getSessionDurations().contains(sessionDuration);
    }

    private Set<String> interviewHistoryTopics(String intervieweeId) {
        Set<String> topics = new HashSet<>();
        sessionRepository.findByCandidateId(intervieweeId).forEach(session ->
                session.getTopics().forEach(topic -> {
                    String normalized = normalizeOption(topic).toLowerCase(Locale.ROOT);
                    if (!normalized.isBlank()) topics.add(normalized);
                }));
        return topics;
    }

    private int topicCompatibility(User interviewee, Set<String> historyTopics, User interviewer) {
        List<String> targets = new ArrayList<>();
        targets.addAll(splitOptions(interviewee.getPreferredDomains()));
        targets.addAll(splitOptions(interviewee.getInterviewTopics()));
        Set<String> normalizedTarget = new HashSet<>();
        targets.forEach(topic -> {
            String normalized = normalizeOption(topic).toLowerCase(Locale.ROOT);
            if (!normalized.isBlank()) normalizedTarget.add(normalized);
        });
        normalizedTarget.addAll(historyTopics);
        if (normalizedTarget.isEmpty()) {
            return 0;
        }
        int score = 0;
        for (String interviewerTopic : combinedInterviewerTopics(interviewer)) {
            String normalized = interviewerTopic.toLowerCase(Locale.ROOT);
            if (normalizedTarget.stream().anyMatch(normalized::contains) || normalizedTarget.contains(normalized)) {
                score += 1;
            }
        }
        return score;
    }

    private double languageMatchScore(User interviewee, User interviewer) {
        Set<String> intervieweeLanguages = normalizedOptionSet(interviewee.getLanguage());
        Set<String> interviewerLanguages = normalizedOptionSet(interviewer.getLanguage());
        if (intervieweeLanguages.isEmpty() || interviewerLanguages.isEmpty()) {
            return 0.0;
        }
        long overlap = intervieweeLanguages.stream().filter(interviewerLanguages::contains).count();
        return overlap * 1.8;
    }

    private double timezoneMatchScore(User interviewee, User interviewer) {
        String intervieweeZone = normalizeOption(interviewee.getTimeZone()).toLowerCase(Locale.ROOT);
        String interviewerZone = normalizeOption(interviewer.getTimeZone()).toLowerCase(Locale.ROOT);
        if (intervieweeZone.isBlank() || interviewerZone.isBlank()) {
            return 0.0;
        }
        if (intervieweeZone.equals(interviewerZone)) {
            return 3.0;
        }
        return interviewerZone.contains(intervieweeZone) || intervieweeZone.contains(interviewerZone) ? 1.25 : 0.0;
    }

    private double availabilityOverlapScore(User interviewee, User interviewer) {
        List<String> preferences = splitOptions(interviewee.getAvailability());
        if (preferences.isEmpty()) {
            return hasMatchingSlot(interviewer, 3, null) ? 1.0 : 0.0;
        }
        List<AvailabilityDtos.GeneratedSlotResponse> slots = availabilitySlotService.generatedSlotResponses(interviewer.getId(), 5, false);
        if (slots.isEmpty()) {
            return 0.0;
        }
        String joined = String.join(" ", preferences).toLowerCase(Locale.ROOT);
        boolean weekdays = joined.contains("weekday");
        boolean weekends = joined.contains("weekend");
        if (!weekdays && !weekends) {
            return 1.5;
        }
        long matches = slots.stream()
                .filter(slot -> slot.getStartTime() != null)
                .filter(slot -> {
                    try {
                        java.time.DayOfWeek day = java.time.OffsetDateTime.parse(slot.getStartTime()).getDayOfWeek();
                        return weekdays ? day.getValue() <= 5 : day.getValue() >= 6;
                    } catch (RuntimeException ex) {
                        return false;
                    }
                })
                .count();
        return matches > 0 ? 2.0 : 0.0;
    }

    private List<String> combinedInterviewerTopics(User interviewer) {
        List<String> combined = new ArrayList<>();
        combined.addAll(splitOptions(interviewer.getSkills()));
        combined.addAll(splitOptions(interviewer.getInterviewTopics()));
        return combined.stream().distinct().toList();
    }

    private Set<String> normalizedOptionSet(String values) {
        return splitOptions(values).stream()
                .map(value -> normalizeOption(value).toLowerCase(Locale.ROOT))
                .filter(value -> !value.isBlank())
                .collect(java.util.stream.Collectors.toSet());
    }

    private String interviewerIdentity(User user) {
        return !isBlank(user.getDisplayName()) ? user.getDisplayName() : user.getUsername();
    }

    private String reviewerName(String reviewerId) {
        return userRepository.findById(reviewerId)
                .map(this::interviewerIdentity)
                .orElse("InterviewPrep member");
    }

    private String reviewerAvatar(String reviewerId) {
        return userRepository.findById(reviewerId).map(User::getAvatarUrl).orElse(null);
    }

    private String sessionTitle(String sessionId) {
        return sessionRepository.findById(sessionId).map(session -> session.getTitle() == null ? "Interview session" : session.getTitle()).orElse("Interview session");
    }

    private MarketplaceDtos.PublicReview toPublicReview(Feedback review) {
        return new MarketplaceDtos.PublicReview(
                review.getId(),
                reviewerName(review.getReviewerId()),
                reviewerAvatar(review.getReviewerId()),
                review.getRating(),
                review.getComments(),
                sessionTitle(review.getSessionId()),
                review.getCreatedAt() == null ? null : review.getCreatedAt().toString(),
                review.getTopicFeedback().stream()
                        .map(topic -> new MarketplaceDtos.TopicReviewSummary(
                                topic.getTopic(),
                                topic.getRating(),
                                topic.getSkillRatings(),
                                topic.getStrengths(),
                                topic.getImprovementAreas(),
                                topic.getComments()
                        ))
                        .toList()
        );
    }

    private MarketplaceDtos.InterviewerCard toInterviewerCard(User user) {
        return new MarketplaceDtos.InterviewerCard(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                user.getCompany(),
                user.getCurrentRole(),
                user.getBio(),
                user.getLanguage(),
                user.getTimeZone(),
                user.getSkills(),
                user.getInterviewTopics(),
                user.getPreferredDomains(),
                user.getSessionDurations(),
                user.getExperienceLevel(),
                user.getYearsExperience(),
                user.getAverageRating(),
                user.getReviewCount(),
                user.getCompletedInterviews(),
                user.getCompletedSessions(),
                user.getCancelledSessions(),
                reliabilityScore(user),
                user.getInterviewerVerified(),
                user.getAcceptingBookings(),
                user.getPublicProfileVisible()
        );
    }

    private double reliabilityScore(User user) {
        int completed = user.getCompletedSessions() == null ? 0 : user.getCompletedSessions();
        int cancelled = user.getCancelledSessions() == null ? 0 : user.getCancelledSessions();
        int total = completed + cancelled;
        if (total <= 0) {
            return 100.0;
        }
        return Math.round((completed * 1000.0) / total) / 10.0;
    }

    private int timezoneAffinityScore(User user, String viewerTimezone) {
        String viewer = normalizeOption(viewerTimezone).toLowerCase(Locale.ROOT);
        if (viewer.isBlank()) {
            return 0;
        }
        String interviewerZone = normalizeOption(user.getTimeZone()).toLowerCase(Locale.ROOT);
        if (interviewerZone.isBlank()) {
            return 0;
        }
        if (viewer.equals(interviewerZone)) {
            return 3;
        }
        if (interviewerZone.contains(viewer) || viewer.contains(interviewerZone)) {
            return 1;
        }
        return 0;
    }

    private int verificationScore(User user) {
        return Boolean.TRUE.equals(user.getInterviewerVerified()) ? 1 : 0;
    }

    private int bookingAvailabilityScore(User user, Map<String, Integer> cache) {
        if (!Boolean.TRUE.equals(user.getAcceptingBookings())) {
            return 0;
        }
        return cache.computeIfAbsent(user.getId(), ignored -> hasMatchingSlot(user, 7, null) ? 2 : 1);
    }

    private String normalizeOption(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private boolean hasRole(User user) {
        return user != null && user.hasRole("INTERVIEWER");
    }

    private boolean isPubliclyVisible(User user) {
        return user != null
                && !Boolean.FALSE.equals(user.getAccountEnabled())
                && !Boolean.FALSE.equals(user.getPublicProfileVisible());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
