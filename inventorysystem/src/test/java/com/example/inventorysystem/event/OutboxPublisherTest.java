package com.example.inventorysystem.event;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OutboxPublisherTest {

    @Mock OutboxEventRepository repository;
    @Mock KafkaTemplate<String, String> kafkaTemplate;
    @Mock PlatformTransactionManager txManager;

    SimpleMeterRegistry meters = new SimpleMeterRegistry();
    OutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        publisher = new OutboxPublisher(repository, kafkaTemplate, txManager, meters, 100, 7);
    }

    @AfterEach
    void clearInterrupt() {
        Thread.interrupted();
    }

    private static OutboxEvent event(long id, String aggregateId) {
        OutboxEvent e = new OutboxEvent("ORDER", aggregateId, "ORDER_RESERVED",
                OutboxService.ORDER_EVENTS_TOPIC, "{\"orderId\":" + aggregateId + "}");
        ReflectionTestUtils.setField(e, "id", id);
        return e;
    }

    private double count(String name) {
        return meters.get(name).counter().count();
    }

    @Test
    void emptyBatchSendsNothing() {
        when(repository.lockNextBatch(100)).thenReturn(List.of());

        publisher.publishPending();

        verify(kafkaTemplate, never()).send(any(ProducerRecord.class));
    }

    @Test
    void publishesBatchWithKeyAndHeadersAndMarksEventsPublished() {
        OutboxEvent a = event(1, "10");
        OutboxEvent b = event(2, "11");
        when(repository.lockNextBatch(100)).thenReturn(List.of(a, b));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        publisher.publishPending();

        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate, times(2)).send(captor.capture());
        ProducerRecord<String, String> first = captor.getAllValues().get(0);
        assertThat(first.topic()).isEqualTo(OutboxService.ORDER_EVENTS_TOPIC);
        assertThat(first.key()).isEqualTo("10");
        assertThat(first.value()).contains("\"orderId\":10");
        assertThat(new String(first.headers().lastHeader("event-id").value(), StandardCharsets.UTF_8)).isEqualTo("1");
        assertThat(new String(first.headers().lastHeader("event-type").value(), StandardCharsets.UTF_8))
                .isEqualTo("ORDER_RESERVED");
        assertThat(a.getPublishedAt()).isNotNull();
        assertThat(b.getPublishedAt()).isNotNull();
        assertThat(count("inventory.outbox.published")).isEqualTo(2.0);
        assertThat(count("inventory.outbox.failed")).isZero();
    }

    @Test
    void enqueueFailureStopsBatchSoLaterEventsCannotOvertake() {
        OutboxEvent a = event(1, "10");
        OutboxEvent b = event(2, "11");
        when(repository.lockNextBatch(100)).thenReturn(List.of(a, b));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)))
                .thenThrow(new IllegalStateException("metadata timeout"));

        publisher.publishPending();

        assertThat(a.getPublishedAt()).isNotNull();
        assertThat(b.getPublishedAt()).isNull();
        assertThat(count("inventory.outbox.failed")).isEqualTo(1.0);
        assertThat(count("inventory.outbox.published")).isEqualTo(1.0);
    }

    @Test
    void brokerRejectionLeavesEventAndRestOfBatchUnpublished() {
        OutboxEvent a = event(1, "10");
        OutboxEvent b = event(2, "11");
        when(repository.lockNextBatch(100)).thenReturn(List.of(a, b));
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker down"));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(failed)
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        publisher.publishPending();

        assertThat(a.getPublishedAt()).isNull();
        assertThat(b.getPublishedAt()).isNull();
        assertThat(count("inventory.outbox.failed")).isEqualTo(1.0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void interruptionWhileWaitingRestoresInterruptFlagAndStops() throws Exception {
        OutboxEvent a = event(1, "10");
        when(repository.lockNextBatch(100)).thenReturn(List.of(a));
        CompletableFuture<SendResult<String, String>> future = mock(CompletableFuture.class);
        when(future.get(15, TimeUnit.SECONDS)).thenThrow(new InterruptedException());
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

        publisher.publishPending();

        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        assertThat(a.getPublishedAt()).isNull();
    }

    @Test
    void repositoryFailureIsLoggedNotThrown() {
        when(repository.lockNextBatch(100)).thenThrow(new IllegalStateException("db down"));

        assertThatCode(() -> publisher.publishPending()).doesNotThrowAnyException();
    }

    @Test
    void pendingGaugeReportsRepositoryCount() {
        when(repository.countByPublishedAtIsNull()).thenReturn(42L);

        assertThat(meters.get("inventory.outbox.pending").gauge().value()).isEqualTo(42.0);
    }

    @Test
    void purgeDeletesEventsPublishedBeforeRetentionCutoff() {
        when(repository.deletePublishedBefore(any(OffsetDateTime.class))).thenReturn(3);

        publisher.purgeOldEvents();

        ArgumentCaptor<OffsetDateTime> cutoff = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(repository).deletePublishedBefore(cutoff.capture());
        assertThat(cutoff.getValue()).isBefore(OffsetDateTime.now().minusDays(6));
        assertThat(cutoff.getValue()).isAfter(OffsetDateTime.now().minusDays(8));
    }

    @Test
    void purgeFailureIsLoggedNotThrown() {
        when(repository.deletePublishedBefore(any())).thenThrow(new IllegalStateException("db down"));

        assertThatCode(() -> publisher.purgeOldEvents()).doesNotThrowAnyException();
        verify(repository, times(1)).deletePublishedBefore(any());
        verify(repository, never()).lockNextBatch(anyInt());
    }

    @Test
    void keepsPublishingWhileBatchesComeBackFull() {
        OutboxPublisher small = new OutboxPublisher(
                repository, kafkaTemplate, txManager, new SimpleMeterRegistry(), 2, 7);
        OutboxEvent a = event(1, "10");
        OutboxEvent b = event(2, "11");
        OutboxEvent c = event(3, "12");
        when(repository.lockNextBatch(2)).thenReturn(List.of(a, b), List.of(c));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        small.publishPending();

        assertThat(a.getPublishedAt()).isNotNull();
        assertThat(b.getPublishedAt()).isNotNull();
        assertThat(c.getPublishedAt()).isNotNull();
        verify(repository, times(2)).lockNextBatch(2);
    }

    @Test
    void stopsAfterAFailedBatchInsteadOfSpinning() {
        OutboxPublisher small = new OutboxPublisher(
                repository, kafkaTemplate, txManager, new SimpleMeterRegistry(), 2, 7);
        OutboxEvent a = event(1, "10");
        OutboxEvent b = event(2, "11");
        when(repository.lockNextBatch(2)).thenReturn(List.of(a, b));
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker down"));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(failed);

        small.publishPending();

        verify(repository, times(1)).lockNextBatch(2);
        assertThat(a.getPublishedAt()).isNull();
    }
}
