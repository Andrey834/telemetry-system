package ru.telemetry.ingestion.service;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.telemetry.ingestion.repository.DeviceRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Проверка API-ключа устройства (заголовок X-Device-Key) по реестру devices (read-реплика
 * Postgres). SHA-256, не bcrypt — ключ высокоэнтропийный сгенерированный токен, а не пароль
 * человека, детерминированный хеш достаточен и не тормозит каждый POST /telemetry.
 */
@Service
public class DeviceAuthService {

    private final DeviceRepository deviceRepository;

    public DeviceAuthService(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    public Mono<Boolean> isValid(String deviceId, String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return Mono.just(false);
        }
        String candidateHash = sha256Hex(apiKey);
        return deviceRepository.findById(deviceId)
                .map(device -> device.apiKeyHash().equals(candidateHash))
                .defaultIfEmpty(false);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 недоступен в JVM", e);
        }
    }
}
