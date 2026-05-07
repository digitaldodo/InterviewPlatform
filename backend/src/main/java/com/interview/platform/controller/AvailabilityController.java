package com.interview.platform.controller;

import com.interview.platform.api.ApiResponse;
import com.interview.platform.dto.AvailabilityDtos;
import com.interview.platform.exception.UnauthorizedException;
import com.interview.platform.model.User;
import com.interview.platform.security.UserPrincipal;
import com.interview.platform.service.AvailabilityService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/interviewer-availability")
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    public AvailabilityController(AvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<AvailabilityDtos.AvailabilityResponse>>> mine(Authentication authentication) {
        User user = currentUser(authentication);
        return ResponseEntity.ok(ApiResponse.success("Availability fetched", availabilityService.getOwnAvailability(user.getId())));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AvailabilityDtos.AvailabilityResponse>> create(
            Authentication authentication,
            @Valid @RequestBody AvailabilityDtos.UpsertRequest request) {
        User user = currentUser(authentication);
        return ResponseEntity.ok(ApiResponse.success("Availability created", availabilityService.create(user.getId(), request)));
    }

    @PutMapping("/{availabilityId}")
    public ResponseEntity<ApiResponse<AvailabilityDtos.AvailabilityResponse>> update(
            Authentication authentication,
            @PathVariable String availabilityId,
            @Valid @RequestBody AvailabilityDtos.UpsertRequest request) {
        User user = currentUser(authentication);
        return ResponseEntity.ok(ApiResponse.success("Availability updated", availabilityService.update(user.getId(), availabilityId, request)));
    }

    @DeleteMapping("/{availabilityId}")
    public ResponseEntity<ApiResponse<Void>> delete(Authentication authentication, @PathVariable String availabilityId) {
        User user = currentUser(authentication);
        availabilityService.delete(user.getId(), availabilityId);
        return ResponseEntity.ok(ApiResponse.success("Availability deleted", null));
    }

    private User currentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new UnauthorizedException("Authentication required");
        }
        return principal.getUser();
    }
}
