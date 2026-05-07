package com.interview.platform.controller;

import com.interview.platform.api.ApiResponse;
import com.interview.platform.dto.BookingRequest;
import com.interview.platform.exception.UnauthorizedException;
import com.interview.platform.model.Session;
import com.interview.platform.model.User;
import com.interview.platform.security.UserPrincipal;
import com.interview.platform.service.SessionService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {
    private final SessionService sessionService;

    public BookingController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Session>> book(@Valid @RequestBody BookingRequest request, Authentication authentication) {
        User user = currentUser(authentication);
        Session session = new Session();
        session.setInterviewerId(request.getInterviewerId());
        session.setCandidateId(user.getId());
        session.setIntervieweeId(user.getId());
        session.setTopics(request.getTopics());
        session.setInterviewType(request.getInterviewType());
        session.setTitle(request.getTopics() != null && !request.getTopics().isEmpty()
                ? String.join(", ", request.getTopics())
                : request.getInterviewType());
        session.setStartTime(request.getStartTime());
        if (request.getDurationMinutes() != null) {
            session.setDurationMinutes(request.getDurationMinutes());
        }
        session.setNotes(request.getNotes());
        session.setMeetingProvider(request.getMeetingProvider());
        session.setStatus("PENDING");
        return ResponseEntity.ok(ApiResponse.success("Booking created", sessionService.createSession(session)));
    }

    private User currentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new UnauthorizedException("Authentication required");
        }
        return principal.getUser();
    }
}
