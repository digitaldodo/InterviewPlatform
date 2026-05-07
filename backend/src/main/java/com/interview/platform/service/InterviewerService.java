package com.interview.platform.service;

import com.interview.platform.dto.PageResponse;
import com.interview.platform.dto.AvailabilityDtos;
import com.interview.platform.dto.InterviewerFilterOptions;
import com.interview.platform.exception.ResourceNotFoundException;
import com.interview.platform.model.User;
import com.interview.platform.repository.UserRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class InterviewerService {
    private final MongoTemplate mongoTemplate;
    private final UserRepository userRepository;
    private final AvailabilitySlotService availabilitySlotService;

    public InterviewerService(MongoTemplate mongoTemplate, UserRepository userRepository, AvailabilitySlotService availabilitySlotService) {
        this.mongoTemplate = mongoTemplate;
        this.userRepository = userRepository;
        this.availabilitySlotService = availabilitySlotService;
    }

    public PageResponse<User> search(String q, String expertise, String company, String role, Integer minExperience,
                                     Integer maxExperience, Double minRating, Boolean available, Boolean free, String language,
                                     String experienceLevel, Boolean verified, String excludeUserId, String sort, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 24));
        Query query = new Query();
        List<Criteria> criteria = new ArrayList<>();
        criteria.add(new Criteria().orOperator(Criteria.where("roles").is("INTERVIEWER"), Criteria.where("role").is("INTERVIEWER")));
        if (!isBlank(excludeUserId)) criteria.add(Criteria.where("id").ne(excludeUserId.trim()));
        if (!isBlank(q)) {
            Pattern pattern = Pattern.compile(Pattern.quote(q.trim()), Pattern.CASE_INSENSITIVE);
            criteria.add(new Criteria().orOperator(
                    Criteria.where("username").regex(pattern),
                    Criteria.where("company").regex(pattern),
                    Criteria.where("currentRole").regex(pattern),
                    Criteria.where("bio").regex(pattern),
                    Criteria.where("skills").regex(pattern)
            ));
        }
        if (!isBlank(expertise)) criteria.add(Criteria.where("skills").regex(Pattern.compile(Pattern.quote(expertise.trim()), Pattern.CASE_INSENSITIVE)));
        if (!isBlank(company)) criteria.add(Criteria.where("company").regex(Pattern.compile(Pattern.quote(company.trim()), Pattern.CASE_INSENSITIVE)));
        if (!isBlank(role)) criteria.add(Criteria.where("currentRole").regex(Pattern.compile(Pattern.quote(role.trim()), Pattern.CASE_INSENSITIVE)));
        if (!isBlank(language)) criteria.add(Criteria.where("language").regex(Pattern.compile(Pattern.quote(language.trim()), Pattern.CASE_INSENSITIVE)));
        if (minExperience != null) criteria.add(Criteria.where("yearsExperience").gte(minExperience));
        if (maxExperience != null) criteria.add(Criteria.where("yearsExperience").lte(maxExperience));
        if (minRating != null) criteria.add(Criteria.where("averageRating").gte(minRating));
        if (Boolean.TRUE.equals(available)) criteria.add(Criteria.where("acceptingBookings").is(true));
        if (Boolean.TRUE.equals(free)) criteria.add(new Criteria().orOperator(Criteria.where("priceCents").is(0), Criteria.where("priceCents").exists(false)));
        if (!isBlank(experienceLevel)) criteria.add(Criteria.where("experienceLevel").regex(Pattern.compile(Pattern.quote(experienceLevel.trim()), Pattern.CASE_INSENSITIVE)));
        if (Boolean.TRUE.equals(verified)) criteria.add(Criteria.where("isVerified").is(true));
        query.addCriteria(new Criteria().andOperator(criteria.toArray(new Criteria[0])));

        long total = mongoTemplate.count(query, User.class);
        query.with(sortBy(sort));
        query.skip((long) safePage * safeSize).limit(safeSize);
        return new PageResponse<>(mongoTemplate.find(query, User.class), total, safePage, safeSize);
    }

    public User getById(String id) {
        User user = userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Interviewer not found"));
        if (!user.hasRole("INTERVIEWER")) {
            throw new ResourceNotFoundException("Interviewer not found");
        }
        return user;
    }

    public List<User> topRated(String excludeUserId) {
        return userRepository.findByRoleOrderByAverageRatingDesc("INTERVIEWER").stream()
                .filter(user -> isBlank(excludeUserId) || !excludeUserId.equals(user.getId()))
                .limit(6)
                .toList();
    }

    public List<User> recommended(String intervieweeId) {
        List<User> candidates = userRepository.findByRoleAndAcceptingBookingsOrderByCompletedInterviewsDesc("INTERVIEWER", true).stream()
                .filter(user -> isBlank(intervieweeId) || !intervieweeId.equals(user.getId()))
                .limit(60)
                .toList();
        if (isBlank(intervieweeId)) {
            return candidates.stream().limit(8).toList();
        }
        User interviewee = userRepository.findById(intervieweeId).orElse(null);
        if (interviewee == null) {
            return candidates.stream().limit(8).toList();
        }
        return candidates.stream()
                .sorted(Comparator.comparingDouble(user -> -recommendationScore(interviewee, user)))
                .limit(8)
                .toList();
    }

    private double recommendationScore(User interviewee, User interviewer) {
        double rating = interviewer.getAverageRating() == null ? 0.0 : interviewer.getAverageRating();
        int completed = interviewer.getCompletedInterviews() == null ? 0 : interviewer.getCompletedInterviews();
        int years = interviewer.getYearsExperience() == null ? 0 : interviewer.getYearsExperience();
        int overlap = preferenceOverlap(interviewee, interviewer);
        double languageBonus = !isBlank(interviewee.getLanguage()) && !isBlank(interviewer.getLanguage())
                && interviewer.getLanguage().toLowerCase(Locale.ROOT).contains(interviewee.getLanguage().toLowerCase(Locale.ROOT))
                ? 2.5
                : 0.0;
        return (overlap * 6.0) + languageBonus + (rating * 2.0) + (Math.min(30, completed) * 0.15) + (Math.min(15, years) * 0.2);
    }

    private int preferenceOverlap(User interviewee, User interviewer) {
        List<String> preferred = interviewee.getPreferredDomains();
        List<String> skills = interviewer.getSkills();
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
        Query query = new Query(interviewerCriteria());
        query.fields().include("skills").include("language").include("company").include("experienceLevel");
        List<User> interviewers = mongoTemplate.find(query, User.class);
        return new InterviewerFilterOptions(
                uniqueNormalized(interviewers.stream().flatMap(user -> splitOptions(user.getSkills()).stream()).toList()),
                uniqueNormalized(interviewers.stream().flatMap(user -> splitOptions(user.getLanguage()).stream()).toList()),
                uniqueNormalized(interviewers.stream().map(User::getCompany).toList()),
                uniqueNormalized(interviewers.stream().map(User::getExperienceLevel).toList())
        );
    }

    public User toggleFavorite(String userId, String interviewerId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        getById(interviewerId);
        List<String> favorites = new ArrayList<>(user.getFavoriteInterviewerIds());
        if (favorites.contains(interviewerId)) {
            favorites.remove(interviewerId);
        } else {
            favorites.add(interviewerId);
        }
        user.setFavoriteInterviewerIds(favorites);
        return userRepository.save(user);
    }

    private Sort sortBy(String sort) {
        String normalized = sort == null ? "top-rated" : sort.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "most-experienced" -> Sort.by(Sort.Direction.DESC, "yearsExperience");
            case "most-active" -> Sort.by(Sort.Direction.DESC, "completedInterviews");
            case "newest" -> Sort.by(Sort.Direction.DESC, "createdAt");
            default -> Sort.by(Sort.Direction.DESC, "averageRating");
        };
    }

    private Criteria interviewerCriteria() {
        return new Criteria().orOperator(Criteria.where("roles").is("INTERVIEWER"), Criteria.where("role").is("INTERVIEWER"));
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

    private String normalizeOption(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
