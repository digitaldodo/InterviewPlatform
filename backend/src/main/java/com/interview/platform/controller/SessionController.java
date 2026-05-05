package com.interview.platform.controller;

import com.interview.platform.model.Session;
import com.interview.platform.service.SessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/sessions")
public class SessionController {

    @Autowired
    private SessionService sessionService;

    @PostMapping
    public ResponseEntity<Session> createSession(@RequestBody Session session) {
        try {
            return ResponseEntity.ok(sessionService.createSession(session));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping
    public ResponseEntity<List<Session>> getAllSessions() {
        return ResponseEntity.ok(sessionService.getAllSessions());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Session> getSessionById(@PathVariable String id) {
        Optional<Session> session = sessionService.getById(id);
        return session.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
    @PutMapping("/{id}/status")
    public ResponseEntity<Session> updateSessionStatus(@PathVariable String id, @RequestBody java.util.Map<String, String> request) {
        try {
            Session updatedSession = sessionService.updateSessionStatus(id, request.get("status"));
            if (updatedSession != null) {
                return ResponseEntity.ok(updatedSession);
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
