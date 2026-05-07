package com.interview.platform.controller;

import com.interview.platform.api.ApiResponse;
import com.interview.platform.dto.AvailabilityDtos;
import com.interview.platform.dto.InterviewerFilterOptions;
import com.interview.platform.dto.MarketplaceDtos;
import com.interview.platform.dto.PageResponse;
import com.interview.platform.exception.UnauthorizedException;
import com.interview.platform.model.User;
import com.interview.platform.security.UserPrincipal;
import com.interview.platform.service.InterviewerService;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/interviewers")
public class InterviewerController {
    private final InterviewerService interviewerService;

    public InterviewerController(InterviewerService interviewerService) {
        this.interviewerService = interviewerService;
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<PageResponse<MarketplaceDtos.InterviewerCard>>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String expertise,
            @RequestParam(required = false) String company,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Integer minExperience,
            @RequestParam(required = false) Integer maxExperience,
            @RequestParam(required = false) Double minRating,
            @RequestParam(required = false) Boolean available,
            @RequestParam(required = false) Boolean free,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) String experienceLevel,
            @RequestParam(required = false) Boolean verified,
            @RequestParam(required = false) String timezone,
            @RequestParam(required = false) String topic,
            @RequestParam(required = false) Boolean availableToday,
            @RequestParam(required = false) Integer sessionDuration,
            @RequestParam(required = false) String viewerTimezone,
            @RequestParam(required = false) String excludeUserId,
            @RequestParam(defaultValue = "top-rated") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "9") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success("Interviewers fetched",
                interviewerService.search(q, expertise, company, role, minExperience, maxExperience, minRating, available, free, language, experienceLevel, verified, timezone, topic, availableToday, sessionDuration, viewerTimezone, excludeUserId, sort, page, size)));
    }

    @GetMapping("/filter-options")
    public ResponseEntity<ApiResponse<InterviewerFilterOptions>> filterOptions() {
        return ResponseEntity.ok(ApiResponse.success("Interviewer filter options fetched", interviewerService.filterOptions()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MarketplaceDtos.InterviewerCard>> get(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success("Interviewer fetched", interviewerService.getById(id)));
    }

    @GetMapping("/{id}/availability")
    public ResponseEntity<ApiResponse<List<String>>> availability(@PathVariable String id,
                                                                  @RequestParam(required = false) Integer days) {
        return ResponseEntity.ok(ApiResponse.success("Availability fetched", interviewerService.availableSlots(id, days)));
    }

    @GetMapping("/{id}/slots")
    public ResponseEntity<ApiResponse<List<AvailabilityDtos.GeneratedSlotResponse>>> slots(@PathVariable String id,
                                                                                            @RequestParam(required = false, defaultValue = "false") boolean includeUnavailable,
                                                                                            @RequestParam(required = false) Integer days) {
        return ResponseEntity.ok(ApiResponse.success("Generated slots fetched", interviewerService.generatedSlots(id, days, includeUnavailable)));
    }

    @GetMapping("/top-rated")
    public ResponseEntity<ApiResponse<List<MarketplaceDtos.InterviewerCard>>> topRated(@RequestParam(required = false) String excludeUserId) {
        return ResponseEntity.ok(ApiResponse.success("Top-rated interviewers fetched", interviewerService.topRated(excludeUserId)));
    }

    @GetMapping("/recommended")
    public ResponseEntity<ApiResponse<List<MarketplaceDtos.InterviewerCard>>> recommended(@RequestParam(required = false) String intervieweeId) {
        return ResponseEntity.ok(ApiResponse.success("Recommended interviewers fetched", interviewerService.recommended(intervieweeId)));
    }

    @GetMapping("/autocomplete")
    public ResponseEntity<ApiResponse<List<MarketplaceDtos.SearchSuggestion>>> autocomplete(@RequestParam String q) {
        return ResponseEntity.ok(ApiResponse.success("Autocomplete suggestions fetched", interviewerService.autocomplete(q)));
    }

    @GetMapping("/public/{username}")
    public ResponseEntity<ApiResponse<MarketplaceDtos.PublicInterviewerProfile>> publicProfile(@PathVariable String username) {
        return ResponseEntity.ok(ApiResponse.success("Public interviewer profile fetched", interviewerService.publicProfile(username)));
    }

    @PostMapping("/{id}/favorite")
    public ResponseEntity<ApiResponse<User>> favorite(@PathVariable String id,
                                                      @RequestBody(required = false) Map<String, String> request,
                                                      Authentication authentication) {
        User current = currentUser(authentication);
        if (request != null && request.get("userId") != null && !current.getId().equals(request.get("userId"))) {
            throw new UnauthorizedException("You can only update your own favorites");
        }
        return ResponseEntity.ok(ApiResponse.success("Favorite updated", interviewerService.toggleFavorite(current.getId(), id)));
    }

    private User currentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new UnauthorizedException("Authentication required");
        }
        return principal.getUser();
    }
}
