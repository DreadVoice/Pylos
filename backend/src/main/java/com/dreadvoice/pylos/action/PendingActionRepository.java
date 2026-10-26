package com.dreadvoice.pylos.action;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PendingActionRepository extends JpaRepository<PendingAction, UUID> {

    List<PendingAction> findAllByStatusOrderByCreatedAtDesc(ActionStatus status);

    List<PendingAction> findAllByOrderByCreatedAtDesc();

    Optional<PendingAction> findByIdempotencyKey(String idempotencyKey);
}
