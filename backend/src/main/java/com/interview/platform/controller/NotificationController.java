package com.interview.platform.controller;

import com.interview.platform.api.ApiResponse;
import com.interview.platform.model.Notification;
import com.interview.platform.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> list(@RequestParam String userId) {
        List<Notification> notifications = notificationService.list(userId);
        return ResponseEntity.ok(ApiResponse.success("Notifications fetched",
                Map.of("items", notifications, "unread", notificationService.unreadCount(userId))));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Notification>> markRead(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success("Notification marked read", notificationService.markRead(id)));
    }
}
