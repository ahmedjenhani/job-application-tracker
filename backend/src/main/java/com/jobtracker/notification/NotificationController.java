package com.jobtracker.notification;

import com.jobtracker.common.ApiExceptions.NotFoundException;
import com.jobtracker.security.CurrentUser;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    public record NotificationResponse(Long id, Long applicationId, String message, boolean read, Instant createdAt) {
        static NotificationResponse from(Notification n) {
            return new NotificationResponse(n.getId(), n.getApplicationId(), n.getMessage(), n.isRead(),
                    n.getCreatedAt());
        }
    }

    private final NotificationRepository notifications;

    public NotificationController(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<NotificationResponse> list(@AuthenticationPrincipal CurrentUser user) {
        return notifications.findTop50ByUserIdOrderByCreatedAtDesc(user.id()).stream()
                .map(NotificationResponse::from).toList();
    }

    @GetMapping("/unread-count")
    @Transactional(readOnly = true)
    public Map<String, Long> unreadCount(@AuthenticationPrincipal CurrentUser user) {
        return Map.of("count", notifications.countByUserIdAndReadFalse(user.id()));
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void markRead(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        Notification n = notifications.findByIdAndUserId(id, user.id())
                .orElseThrow(() -> new NotFoundException("Notification not found"));
        n.setRead(true);
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void markAllRead(@AuthenticationPrincipal CurrentUser user) {
        notifications.markAllRead(user.id());
    }
}
