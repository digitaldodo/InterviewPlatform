package com.interview.platform.controller;

import com.interview.platform.api.ApiResponse;
import com.interview.platform.dto.AvailabilityDtos;
import com.interview.platform.dto.PageResponse;
import com.interview.platform.model.User;
import com.interview.platform.service.InterviewerService;
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
    public ResponseEntity<ApiResponse<PageResponse<User>>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String expertise,
            @RequestParam(required = false) String company,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Integer minExperience,
            @RequestParam(required = false) Double minRating,
            @RequestParam(required = false) Boolean available,
            @RequestParam(required = false) Boolean free,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) String excludeUserId,
            @RequestParam(defaultValue = "top-rated") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "9") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success("Interviewers fetched",
                interviewerService.search(q, expertise, company, role, minExperience, minRating, available, free, language, excludeUserId, sort, page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<User>> get(@PathVariable String id) {
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
    public ResponseEntity<ApiResponse<List<User>>> topRated(@RequestParam(required = false) String excludeUserId) {
        return ResponseEntity.ok(ApiResponse.success("Top-rated interviewers fetched", interviewerService.topRated(excludeUserId)));
    }

    @GetMapping("/recommended")
    public ResponseEntity<ApiResponse<List<User>>> recommended(@RequestParam(required = false) String intervieweeId) {
        return ResponseEntity.ok(ApiResponse.success("Recommended interviewers fetched", interviewerService.recommended(intervieweeId)));
    }

    @PostMapping("/{id}/favorite")
    public ResponseEntity<ApiResponse<User>> favorite(@PathVariable String id, @RequestBody Map<String, String> request) {
        return ResponseEntity.ok(ApiResponse.success("Favorite updated", interviewerService.toggleFavorite(request.get("userId"), id)));
    }
}
