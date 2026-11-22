package com.example.inventorysystem.support;

import org.springframework.boot.test.context.SpringBootTest;

import java.util.TimeZone;

/**
 * Boots the full application against a throw-away PostgreSQL 17 container.
 *
 * <p>The {@code jdbc:tc:} URL makes Testcontainers start (and reuse, for the
 * life of the JVM) the container itself, so no container API is needed here.
 * Flyway runs the real migrations, so the DB CHECK constraints the
 * concurrency tests rely on are the production ones. Requires Docker; these
 * classes are named {@code *IT} and run in {@code ./mvnw verify}, not
 * {@code ./mvnw test}.
 *
 * <p>Redis and Kafka are not started: the scheduled jobs and listener are
 * parked so tests exercise only PostgreSQL behaviour.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:17:///inventory_db",
        "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
        "spring.datasource.username=test",
        "spring.datasource.password=test",
        "spring.datasource.hikari.maximum-pool-size=40",
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.admin.auto-create=false",
        "spring.kafka.producer.properties.max.block.ms=200",
        "inventory.outbox-poll-ms=3600000",
        "inventory.order-expiry-delay-ms=3600000",
        "inventory.security.api-key=it-api-key",
        "inventory.security.webhook-secret=it-webhook-secret"
})
public abstract class PostgresIntegrationBase {

    static {
        // Same as InventorysystemApplication.main(): the PostgreSQL driver sends
        // the JVM timezone to the server, and "Asia/Calcutta" is rejected by the
        // postgres:17 image. Tests never run main().
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }
}
