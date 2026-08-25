package ru.telemetry.query.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** Реестр устройств (read-реплика Postgres) — имя и группа для dashboard. */
@Table("devices")
public record Device(@Id String deviceId, String name, String groupName, boolean active) {
}
