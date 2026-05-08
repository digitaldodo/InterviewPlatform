package com.interview.platform.service;

import com.interview.platform.model.Notification;
import com.interview.platform.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>> emittersByUser = new ConcurrentHashMap<>();

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public Notification create(String userId, String type, String title, String message) {
        return create(userId, type, title, message, Map.of());
    }

    public Notification create(String userId, String type, String title, String message, Map<String, Object> data) {
        if (userId == null || userId.isBlank()) return null;
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setCreatedAt(Instant.now());
        notification.setData(data);
        Notification saved = notificationRepository.save(notification);
        publish(userId, saved);
        return saved;
    }

    public List<Notification> list(String userId) {
        return notificationRepository.findTop20ByUserIdOrderByCreatedAtDesc(userId);
    }

    public List<Notification> list(String userId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 50));
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(
                userId,
                PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
    }

    public long unreadCount(String userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    public Notification markReadForUser(String userId, String id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found"));
        if (userId == null || userId.isBlank() || !userId.equals(notification.getUserId())) {
            throw new IllegalArgumentException("Notification not found");
        }
        if (!notification.isRead()) {
            notification.setRead(true);
            notification.setReadAt(Instant.now());
        }
        return notificationRepository.save(notification);
    }

    public long markAllRead(String userId) {
        if (userId == null || userId.isBlank()) return 0;
        List<Notification> unread = notificationRepository.findByUserIdOrderByCreatedAtDesc(
                userId,
                PageRequest.of(0, 200, Sort.by(Sort.Direction.DESC, "createdAt"))
        ).stream().filter(item -> !item.isRead()).toList();
        if (unread.isEmpty()) return 0;
        Instant now = Instant.now();
        List<Notification> updated = new ArrayList<>(unread.size());
        for (Notification item : unread) {
            item.setRead(true);
            item.setReadAt(now);
            updated.add(item);
        }
        notificationRepository.saveAll(updated);
        return updated.size();
    }

    public SseEmitter subscribe(String userId) {
        SseEmitter emitter = new SseEmitter(0L);
        emittersByUser.computeIfAbsent(userId, ignored -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(userId, emitter));
        emitter.onTimeout(() -> removeEmitter(userId, emitter));
        emitter.onError(ignored -> removeEmitter(userId, emitter));

        try {
            emitter.send(SseEmitter.event().name("connected").data("ok").reconnectTime(2000));
        } catch (IOException ex) {
            removeEmitter(userId, emitter);
        }
        return emitter;
    }

    public int activeEmitterCount() {
        return emittersByUser.values().stream().mapToInt(List::size).sum();
    }

    public int activeUsersWithEmitters() {
        return emittersByUser.size();
    }

    private void publish(String userId, Notification notification) {
        if (userId == null || userId.isBlank() || notification == null) return;
        List<SseEmitter> emitters = emittersByUser.get(userId);
        if (emitters == null || emitters.isEmpty()) return;
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("notification")
                        .data(notification, MediaType.APPLICATION_JSON));
            } catch (IOException ex) {
                removeEmitter(userId, emitter);
            } catch (IllegalStateException ex) {
                removeEmitter(userId, emitter);
            }
        }
    }

    private void removeEmitter(String userId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> emitters = emittersByUser.get(userId);
        if (emitters == null) return;
        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            emittersByUser.remove(userId);
        }
    }
}
