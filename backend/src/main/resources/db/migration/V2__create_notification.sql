create table notification (
    id uuid not null,
    event_id varchar(128) not null,
    event_type varchar(64) not null,
    summary varchar(512) not null,
    invoice_id varchar(128),
    currency_code varchar(8),
    amount varchar(32),
    occurred_at timestamp(6) not null,
    read_at timestamp(6),
    created_at timestamp(6) not null,
    constraint pk_notification primary key (id)
);

create unique index ux_notification_event_id on notification (event_id);

create index ix_notification_occurred_at on notification (occurred_at);
