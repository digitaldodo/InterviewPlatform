package com.interview.platform.controller;

import com.interview.platform.api.ApiResponse;
import com.interview.platform.dto.BookingRequest;
import com.interview.platform.model.Session;
import com.interview.platform.service.SessionService;
import jakarta.validation.Valid;
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
    public ResponseEntity<ApiResponse<Session>> book(@Valid @RequestBody BookingRequest request) {
        Session session = new Session();
        session.setInterviewerId(request.getInterviewerId());
        session.setCandidateId(request.getIntervieweeId());
        session.setInterviewType(request.getInterviewType());
        session.setTitle(request.getInterviewType());
        session.setStartTime(request.getStartTime());
        session.setDurationMinutes(request.getDurationMinutes());
        session.setNotes(request.getNotes());
        session.setStatus("PENDING");
        return ResponseEntity.ok(ApiResponse.success("Booking created", sessionService.createSession(session)));
    }
}
