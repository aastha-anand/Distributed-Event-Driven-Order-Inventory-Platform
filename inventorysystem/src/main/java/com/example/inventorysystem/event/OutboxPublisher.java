package com.example.inventorysystem.event;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Relays outbox rows to Kafka (at-least-once).
 *
 * <p>The whole batch is handed to the producer first and only then awaited, so
 * a batch costs roughly one broker round-trip instead of one per event while
 * the row locks are held. Records are keyed by order id, so Kafka keeps the
 * events of one order in order; the idempotent producer preserves that order
 * across retries. On the first failure the rest of the batch stays
 * unpublished and is retried next poll: consumers can therefore see a
 * duplicate and must dedupe on the {@code event-id} header.
 */
@Service
public class OutboxPublisher {
    private static final Logger log=
            LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final TransactionTemplate transactionTemplate;
    private final int batchSize;
    private final long retentionDays;
    private final Counter publishedCounter;
    private final Counter failedCounter;

    public OutboxPublisher(
            OutboxEventRepository outboxRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            PlatformTransactionManager transactionManager,
            MeterRegistry meterRegistry,
            @Value("${inventory.outbox-batch-size:100}") int batchSize,
            @Value("${inventory.outbox-retention-days:7}") long retentionDays) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.batchSize = batchSize;
        this.retentionDays = retentionDays;
        this.publishedCounter = Counter.builder("inventory.outbox.published")
                .description("Outbox events delivered to Kafka")
                .register(meterRegistry);
        this.failedCounter = Counter.builder("inventory.outbox.failed")
                .description("Outbox publish attempts that failed")
                .register(meterRegistry);
        Gauge.builder("inventory.outbox.pending", outboxRepository,
                        OutboxEventRepository::countByPublishedAtIsNull)
                .description("Outbox events waiting to be published (lag)")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${inventory.outbox-poll-ms:1000}")
    public void publishPending() {
        try {
            transactionTemplate.executeWithoutResult(status -> sendBatch());
        } catch (RuntimeException e) {
            log.error("Outbox publishing failed; will retry", e);
        }
    }

    private void sendBatch() {
        List<OutboxEvent> batch = outboxRepository.lockNextBatch(batchSize);
        if (batch.isEmpty()) {
            return;
        }

        List<CompletableFuture<?>> inFlight = new ArrayList<>(batch.size());
        for (OutboxEvent event : batch) {
            try {
                inFlight.add(kafkaTemplate.send(toRecord(event)));
            } catch (RuntimeException e) {
                // send() itself failed (e.g. metadata fetch timed out);
                // stop here so later events cannot overtake this one.
                failedCounter.increment();
                log.warn("Could not enqueue outbox event {} ({}); retrying later",
                        event.getId(), e.toString());
                break;
            }
        }

        for (int i = 0; i < inFlight.size(); i++) {
            OutboxEvent event = batch.get(i);
            try {
                inFlight.get(i).get(15, TimeUnit.SECONDS);
                event.markPublished();
                publishedCounter.increment();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                failedCounter.increment();
                log.warn("Could not publish outbox event {} ({}); retrying later",
                        event.getId(), e.toString());
                return;
            }
        }
    }

    private ProducerRecord<String, String> toRecord(OutboxEvent event) {
        ProducerRecord<String, String> record = new ProducerRecord<>(
                event.getTopic(),
                event.getAggregateId(),
                event.getPayload());
        record.headers().add("event-id",
                String.valueOf(event.getId())
                        .getBytes(StandardCharsets.UTF_8));
        record.headers().add("event-type",
                event.getEventType().getBytes(StandardCharsets.UTF_8));
        return record;
    }

    @Scheduled(cron = "0 0 3 * * *")
    public void purgeOldEvents() {
        try {
            OffsetDateTime cutoff =
                    OffsetDateTime.now(ZoneOffset.UTC).minusDays(retentionDays);
            Integer deleted = transactionTemplate.execute(
                    status -> outboxRepository.deletePublishedBefore(cutoff));
            log.info("Purged {} published outbox events", deleted);
        } catch (RuntimeException e) {
            log.error("Outbox purge failed", e);
        }
    }

}
