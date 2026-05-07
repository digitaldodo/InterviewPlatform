package com.interview.platform.service;

import com.interview.platform.model.Notification;
import com.interview.platform.repository.NotificationRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class NotificationService {
    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public Notification create(String userId, String type, String title, String message) {
        if (userId == null || userId.isBlank()) return null;
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setCreatedAt(Instant.now());
        return notificationRepository.save(notification);
    }

    public List<Notification> list(String userId) {
        return notificationRepository.findTop20ByUserIdOrderByCreatedAtDesc(userId);
    }

    public long unreadCount(String userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    public Notification markRead(String id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found"));
        notification.setRead(true);
        return notificationRepository.save(notification);
    }
}
