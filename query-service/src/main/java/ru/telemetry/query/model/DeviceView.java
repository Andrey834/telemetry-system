package ru.telemetry.query.model;

import java.time.Instant;

/**
 * Ответ GET /devices — реестр устройств (имя/группа, Postgres read-реплика) объединён с
 * текущим состоянием из Redis. Устройство, ни разу не приславшее данные или "протухшее" по TTL
 * в Redis, всё равно попадает в список (status=OFFLINE, координаты null) — иначе оно просто
 * молча исчезает, и на dashboard не видно, что оно вообще пропало.
 */
public record DeviceView(
        String deviceId,
        String name,
        String groupName,
        boolean active,
        DeviceStatus status,
        Double lat,
        Double lon,
        Double speedKmh,
        Instant recordedAt
) {
}
