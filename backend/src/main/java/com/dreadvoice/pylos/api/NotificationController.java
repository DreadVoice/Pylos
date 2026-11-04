package com.dreadvoice.pylos.api;

import com.dreadvoice.pylos.notification.Notification;
import com.dreadvoice.pylos.notification.NotificationRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationRepository repository;
    private final Clock clock;

    public NotificationController(NotificationRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @GetMapping
    public NotificationsView list(@RequestParam(required = false, defaultValue = "false") boolean unread) {
        List<Notification> found = unread
                ? repository.findByReadAtIsNullOrderByOccurredAtDesc()
                : repository.findTop50ByOrderByOccurredAtDesc();

        return new NotificationsView(
                found.stream().map(NotificationController::toView).toList(),
                repository.countByReadAtIsNull());
    }

    @PostMapping("/mark-read")
    public NotificationsView markRead() {
        repository.markAllRead(clock.instant());
        return list(false);
    }

    private static NotificationView toView(Notification notification) {
        return new NotificationView(
                notification.getId(),
                notification.getEventType(),
                notification.getSummary(),
                notification.getInvoiceId(),
                notification.getCurrencyCode(),
                notification.getAmount(),
                notification.getOccurredAt(),
                notification.getReadAt() != null);
    }
}
