create table notification_settings (
    id bigserial primary key,
    user_id bigint not null,
    browser_notifications_enabled boolean not null default false,
    shift_reminders_enabled boolean not null default true,
    shift_reminder_minutes_before integer not null default 60,
    tomorrow_digest_enabled boolean not null default false,
    tomorrow_digest_time time not null default time '19:00',
    task_reminders_enabled boolean not null default true,
    task_reminder_time time not null default time '09:00',
    important_day_reminders_enabled boolean not null default true,
    important_day_days_before integer not null default 1,
    important_day_reminder_time time not null default time '09:00',
    updated_at timestamp not null default current_timestamp,
    constraint uk_notification_settings_user unique (user_id),
    constraint fk_notification_settings_user foreign key (user_id) references users(id) on delete cascade,
    constraint chk_notification_shift_minutes check (shift_reminder_minutes_before >= 0 and shift_reminder_minutes_before <= 1440),
    constraint chk_notification_important_days check (important_day_days_before >= 0 and important_day_days_before <= 366)
);
