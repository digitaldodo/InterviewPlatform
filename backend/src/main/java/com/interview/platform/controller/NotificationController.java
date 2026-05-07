package com.interview.platform.controller;

import com.interview.platform.api.ApiResponse;
import com.interview.platform.exception.UnauthorizedException;
import com.interview.platform.model.Notification;
import com.interview.platform.model.User;
import com.interview.platform.security.UserPrincipal;
import com.interview.platform.service.NotificationService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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
    public ResponseEntity<ApiResponse<Map<String, Object>>> list(@RequestParam(required = false) String userId,
                                                                @RequestParam(required = false, defaultValue = "20") int limit,
                                                                Authentication authentication) {
        User current = currentUser(authentication);
        String effectiveUserId = (userId == null || userId.isBlank()) ? current.getId() : userId.trim();
        if (!current.getId().equals(effectiveUserId)) {
            throw new UnauthorizedException("You can only access your own notifications");
        }
        List<Notification> notifications = notificationService.list(effectiveUserId, limit);
        return ResponseEntity.ok(ApiResponse.success("Notifications fetched",
                Map.of("items", notifications, "unread", notificationService.unreadCount(effectiveUserId))));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Notification>> markRead(@PathVariable String id, Authentication authentication) {
        User current = currentUser(authentication);
        return ResponseEntity.ok(ApiResponse.success("Notification marked read", notificationService.markReadForUser(current.getId(), id)));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<Map<String, Object>>> markAllRead(Authentication authentication) {
        User current = currentUser(authentication);
        long updated = notificationService.markAllRead(current.getId());
        return ResponseEntity.ok(ApiResponse.success("Notifications marked read",
                Map.of("updated", updated, "unread", notificationService.unreadCount(current.getId()))));
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(Authentication authentication) {
        User current = currentUser(authentication);
        return notificationService.subscribe(current.getId());
    }

    private User currentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new UnauthorizedException("Authentication required");
        }
        return principal.getUser();
    }
}
