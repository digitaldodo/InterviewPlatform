package com.interview.platform.controller;

import com.interview.platform.api.ApiResponse;
import com.interview.platform.dto.MeetingDtos;
import com.interview.platform.exception.ResourceNotFoundException;
import com.interview.platform.exception.UnauthorizedException;
import com.interview.platform.model.Session;
import com.interview.platform.model.User;
import com.interview.platform.security.UserPrincipal;
import com.interview.platform.service.CalendarInviteService;
import com.interview.platform.service.SessionService;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Session>> createSession(@RequestBody Session session, Authentication authentication) {
        User actor = currentUser(authentication);
        session.setCandidateId(actor.getId());
        session.setIntervieweeId(actor.getId());
        return ResponseEntity.ok(ApiResponse.success("Session created successfully", sessionService.createSession(session)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Session>>> getAllSessions(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Sessions fetched successfully", sessionService.getSessionsForUser(currentUser(authentication))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Session>> getSessionById(@PathVariable String id, Authentication authentication) {
        Session session = sessionService.getById(id)
                .map(found -> sessionService.getByIdForUser(found.getId(), currentUser(authentication)))
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));
        return ResponseEntity.ok(ApiResponse.success("Session fetched successfully", session));
    }

    @GetMapping("/interviewer/{id}")
    public ResponseEntity<ApiResponse<List<Session>>> getSessionsByInterviewer(@PathVariable String id, Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Sessions fetched successfully", sessionService.getByInterviewerId(id, currentUser(authentication))));
    }

    @GetMapping("/interviewee/{id}")
    public ResponseEntity<ApiResponse<List<Session>>> getSessionsByInterviewee(@PathVariable String id, Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Sessions fetched successfully", sessionService.getByIntervieweeId(id, currentUser(authentication))));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<ApiResponse<Session>> updateSessionStatus(@PathVariable String id, @RequestBody Map<String, String> request,
                                                                    Authentication authentication) {
        Session updatedSession = sessionService.updateSessionStatus(id, request.get("status"), currentUser(authentication));
        return ResponseEntity.ok(ApiResponse.success("Session status updated successfully", updatedSession));
    }

    @PatchMapping("/{id}/reschedule")
    public ResponseEntity<ApiResponse<Session>> rescheduleSession(@PathVariable String id,
                                                                  @RequestBody Map<String, String> request,
                                                                  Authentication authentication) {
        Integer durationMinutes = null;
        if (request.get("durationMinutes") != null && !request.get("durationMinutes").isBlank()) {
            durationMinutes = Integer.parseInt(request.get("durationMinutes"));
        }
        Session updatedSession = sessionService.rescheduleSession(id, request.get("startTime"), durationMinutes, currentUser(authentication));
        return ResponseEntity.ok(ApiResponse.success("Session rescheduled successfully", updatedSession));
    }

    @GetMapping("/meeting-providers")
    public ResponseEntity<ApiResponse<List<MeetingDtos.MeetingProviderOption>>> getMeetingProviders() {
        return ResponseEntity.ok(ApiResponse.success("Meeting providers fetched successfully", sessionService.getMeetingProviderOptions()));
    }

    @GetMapping("/{id}/meeting-access")
    public ResponseEntity<ApiResponse<MeetingDtos.MeetingAccessResponse>> getMeetingAccess(@PathVariable String id,
                                                                                            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Meeting access fetched successfully",
                sessionService.getMeetingAccess(id, currentUser(authentication))));
    }

    @GetMapping(value = "/{id}/calendar.ics", produces = "text/calendar")
    public ResponseEntity<byte[]> downloadCalendarInvite(@PathVariable String id, Authentication authentication) {
        CalendarInviteService.CalendarInvite invite = sessionService.calendarInviteForSession(id, currentUser(authentication));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/calendar; charset=UTF-8; method=" + invite.method()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + invite.filename() + "\"")
                .body(invite.content().getBytes(StandardCharsets.UTF_8));
    }

    @PostMapping("/{id}/meeting/start")
    public ResponseEntity<ApiResponse<MeetingDtos.MeetingAccessResponse>> startMeeting(@PathVariable String id,
                                                                                        Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Meeting started successfully",
                sessionService.startMeeting(id, currentUser(authentication))));
    }

    @PatchMapping("/{id}/confirm")
    public ResponseEntity<ApiResponse<Session>> confirmSession(@PathVariable String id, Authentication authentication) {
        return updateStatus(id, "CONFIRMED", authentication);
    }

    @PatchMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<Session>> completeSession(@PathVariable String id, Authentication authentication) {
        return updateStatus(id, "COMPLETED", authentication);
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<Session>> cancelSession(@PathVariable String id, Authentication authentication) {
        return updateStatus(id, "CANCELLED", authentication);
    }

    private ResponseEntity<ApiResponse<Session>> updateStatus(String id, String status, Authentication authentication) {
        Session updatedSession = sessionService.updateSessionStatus(id, status, currentUser(authentication));
        return ResponseEntity.ok(ApiResponse.success("Session status updated successfully", updatedSession));
    }

    private User currentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new UnauthorizedException("Authentication required");
        }
        return principal.getUser();
    }
}
