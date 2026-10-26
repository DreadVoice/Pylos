create table pending_action (
    id uuid not null,
    action_type varchar(32) not null,
    payload text not null,
    summary varchar(512) not null,
    status varchar(16) not null,
    idempotency_key varchar(128) not null,
    result_reference varchar(128),
    error_message text,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    constraint pk_pending_action primary key (id)
);

create unique index ux_pending_action_idempotency_key on pending_action (idempotency_key);

create index ix_pending_action_status on pending_action (status);
