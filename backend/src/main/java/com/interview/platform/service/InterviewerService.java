package com.interview.platform.service;

import com.interview.platform.dto.PageResponse;
import com.interview.platform.dto.AvailabilityDtos;
import com.interview.platform.exception.ResourceNotFoundException;
import com.interview.platform.model.User;
import com.interview.platform.repository.UserRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
                                     Double minRating, Boolean available, Boolean free, String language,
                                     String excludeUserId, String sort, int page, int size) {
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
        if (minRating != null) criteria.add(Criteria.where("averageRating").gte(minRating));
        if (Boolean.TRUE.equals(available)) criteria.add(Criteria.where("acceptingBookings").is(true));
        if (Boolean.TRUE.equals(free)) criteria.add(new Criteria().orOperator(Criteria.where("priceCents").is(0), Criteria.where("priceCents").exists(false)));
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
        return userRepository.findByRoleAndAcceptingBookingsOrderByCompletedInterviewsDesc("INTERVIEWER", true).stream()
                .filter(user -> isBlank(intervieweeId) || !intervieweeId.equals(user.getId()))
                .limit(8)
                .toList();
    }

    public List<String> availableSlots(String interviewerId, Integer days) {
        getById(interviewerId);
        return availabilitySlotService.availableSlotStartTimes(interviewerId, days);
    }

    public List<AvailabilityDtos.GeneratedSlotResponse> generatedSlots(String interviewerId, Integer days) {
        getById(interviewerId);
        return availabilitySlotService.generatedSlotResponses(interviewerId, days);
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

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
