package ru.telemetry.query.service;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import ru.telemetry.query.config.PrimaryDb;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Регистрация нового устройства (ADMIN) — единственная write-операция query-service, идёт через
 * PrimaryDb (primary), а не read-реплику, которой пользуется остальной сервис.
 */
@Service
public class DeviceAdminService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final PrimaryDb primaryDb;

    public DeviceAdminService(PrimaryDb primaryDb) {
        this.primaryDb = primaryDb;
    }

    public record RegisteredDevice(String deviceId, String name, String groupName, String apiKey) {
    }

    /** apiKey отдаётся вызывающему ровно один раз здесь — в БД остаётся только его SHA-256 хеш,
     * тем же алгоритмом, что ingestion-service проверяет заголовок X-Device-Key. */
    public Mono<RegisteredDevice> register(String deviceId, String name, String groupName) {
        String apiKey = generateApiKey();
        String apiKeyHash = sha256Hex(apiKey);
        return primaryDb.client()
                .sql("""
                        INSERT INTO devices (device_id, name, group_name, api_key_hash)
                        VALUES (:deviceId, :name, :groupName, :apiKeyHash)
                        """)
                .bind("deviceId", deviceId)
                .bind("name", name)
                .bind("groupName", groupName)
                .bind("apiKeyHash", apiKeyHash)
                .fetch()
                .rowsUpdated()
                .thenReturn(new RegisteredDevice(deviceId, name, groupName, apiKey))
                .onErrorMap(DuplicateKeyException.class, e -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "Устройство с таким deviceId уже существует"));
    }

    /** Переименование/смена группы/деактивация — та же запись через PrimaryDb, что и register(). */
    public Mono<Void> update(String deviceId, String name, String groupName, boolean active) {
        return primaryDb.client()
                .sql("""
                        UPDATE devices SET name = :name, group_name = :groupName, active = :active
                        WHERE device_id = :deviceId
                        """)
                .bind("deviceId", deviceId)
                .bind("name", name)
                .bind("groupName", groupName)
                .bind("active", active)
                .fetch()
                .rowsUpdated()
                .flatMap(rows -> rows == 0
                        ? Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Устройство не найдено"))
                        : Mono.empty());
    }

    // package-private (не private) — так же, как DeviceAuthService.sha256Hex в ingestion-service,
    // тестируем напрямую без мока DatabaseClient.
    static String generateApiKey() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 недоступен в JVM", e);
        }
    }
}
