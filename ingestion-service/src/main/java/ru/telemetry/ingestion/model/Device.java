package ru.telemetry.ingestion.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("devices")
public record Device(@Id String deviceId, String name, String groupName, String apiKeyHash, boolean active) {
}
