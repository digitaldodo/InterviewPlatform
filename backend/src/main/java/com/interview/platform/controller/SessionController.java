package com.interview.platform.controller;

import com.interview.platform.api.ApiResponse;
import com.interview.platform.exception.ResourceNotFoundException;
import com.interview.platform.model.Session;
import com.interview.platform.service.SessionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    public ResponseEntity<ApiResponse<Session>> createSession(@RequestBody Session session) {
        return ResponseEntity.ok(ApiResponse.success("Session created successfully", sessionService.createSession(session)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Session>>> getAllSessions() {
        return ResponseEntity.ok(ApiResponse.success("Sessions fetched successfully", sessionService.getAllSessions()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Session>> getSessionById(@PathVariable String id) {
        Session session = sessionService.getById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));
        return ResponseEntity.ok(ApiResponse.success("Session fetched successfully", session));
    }

    @GetMapping("/interviewer/{id}")
    public ResponseEntity<ApiResponse<List<Session>>> getSessionsByInterviewer(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success("Sessions fetched successfully", sessionService.getByInterviewerId(id)));
    }

    @GetMapping("/interviewee/{id}")
    public ResponseEntity<ApiResponse<List<Session>>> getSessionsByInterviewee(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success("Sessions fetched successfully", sessionService.getByIntervieweeId(id)));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<ApiResponse<Session>> updateSessionStatus(@PathVariable String id, @RequestBody Map<String, String> request) {
        Session updatedSession = sessionService.updateSessionStatus(id, request.get("status"));
        return ResponseEntity.ok(ApiResponse.success("Session status updated successfully", updatedSession));
    }

    @PatchMapping("/{id}/confirm")
    public ResponseEntity<ApiResponse<Session>> confirmSession(@PathVariable String id) {
        return updateStatus(id, "CONFIRMED");
    }

    @PatchMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<Session>> completeSession(@PathVariable String id) {
        return updateStatus(id, "COMPLETED");
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<Session>> cancelSession(@PathVariable String id) {
        return updateStatus(id, "CANCELLED");
    }

    private ResponseEntity<ApiResponse<Session>> updateStatus(String id, String status) {
        Session updatedSession = sessionService.updateSessionStatus(id, status);
        return ResponseEntity.ok(ApiResponse.success("Session status updated successfully", updatedSession));
    }
}
