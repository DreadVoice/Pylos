package com.dreadvoice.pylos.api;

import java.util.List;

public record NotificationsView(
        List<NotificationView> notifications,
        long unreadCount
) {
}
