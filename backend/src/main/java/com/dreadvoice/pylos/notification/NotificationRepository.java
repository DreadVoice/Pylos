package com.dreadvoice.pylos.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findTop50ByOrderByOccurredAtDesc();

    List<Notification> findByReadAtIsNullOrderByOccurredAtDesc();

    boolean existsByEventId(String eventId);

    long countByReadAtIsNull();

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Notification n set n.readAt = :now where n.readAt is null")
    int markAllRead(@Param("now") Instant now);
}
