package com.dreadvoice.pylos.action;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PendingActionRepository extends JpaRepository<PendingAction, UUID> {

    List<PendingAction> findAllByStatusOrderByCreatedAtDesc(ActionStatus status);

    List<PendingAction> findAllByOrderByCreatedAtDesc();

    Optional<PendingAction> findByIdempotencyKey(String idempotencyKey);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update PendingAction a set a.status = :to, a.updatedAt = :now "
            + "where a.id = :id and a.status = :from")
    int compareAndSetStatus(@Param("id") UUID id,
                            @Param("from") ActionStatus from,
                            @Param("to") ActionStatus to,
                            @Param("now") Instant now);
}
