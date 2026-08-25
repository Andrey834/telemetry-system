package ru.telemetry.query.config;

import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;

/**
 * DatabaseClient на primary Postgres — только для редких write-операций (регистрация устройства).
 * Весь остальной сервис читает с реплики через автоконфигурированные spring.r2dbc.* бины
 * (DB_REPLICA_HOST). ConnectionFactory/DatabaseClient здесь собраны вручную и намеренно НЕ
 * объявлены как @Bean этих типов — иначе Spring Boot увидел бы второй бин ConnectionFactory/
 * DatabaseClient и отключил бы автоконфигурацию R2DBC для реплики (она активна только пока
 * @ConditionalOnMissingBean(ConnectionFactory.class)/(DatabaseClient.class) не находит других
 * бинов этого типа) — эти объекты живут как приватное поле обычного @Component, невидимое для
 * этой проверки.
 */
@Component
public class PrimaryDb {

    private final DatabaseClient client;

    public PrimaryDb(@Value("${app.db.primary.host}") String host,
                      @Value("${app.db.primary.port}") int port,
                      @Value("${app.db.primary.name}") String database,
                      @Value("${app.db.primary.user}") String user,
                      @Value("${app.db.primary.password}") String password,
                      @Value("${app.db.primary.ssl-mode}") String sslMode) {
        ConnectionFactory connectionFactory = ConnectionFactories.get(
                ConnectionFactoryOptions.parse(
                                "r2dbc:postgresql://%s:%d/%s?sslMode=%s".formatted(host, port, database, sslMode))
                        .mutate()
                        .option(ConnectionFactoryOptions.USER, user)
                        .option(ConnectionFactoryOptions.PASSWORD, password)
                        .build());
        this.client = DatabaseClient.create(connectionFactory);
    }

    public DatabaseClient client() {
        return client;
    }
}
