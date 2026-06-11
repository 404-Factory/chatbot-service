package com.factory.chatbot.infrastructure.kafka;

import com.factory.common.event.domain.DomainEvent;
import com.factory.common.event.domain.EventPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * [WORKAROUND] 임시 방편 Kafka Outbox Publisher
 *
 * <p>
 * 이 클래스는 로컬 개발 환경 등에서 Debezium CDC 툴이나 Outbox Poller가 동작하지 않을 때
 * Outbox 테이블에 쌓인 이벤트를 실제로 Kafka 브로커로 발행해주기 위한 임시방편용 클래스입니다.
 * </p>
 *
 * <p>
 * 비즈니스 트랜잭션이 최종 성공하여 데이터베이스에 커밋된 직후 (AFTER_COMMIT) 이벤트를 감지해
 * {@link KafkaTemplate}을 사용해 직접 Kafka 토픽으로 발행합니다.
 * </p>
 *
 * <p>
 * <strong>주의:</strong> 운영 환경 등에서 별도의 CDC 툴(Debezium 등)을 구동하게 되면 이 클래스는 중복 발행을
 * 유발하므로 <strong>삭제해야 합니다.</strong>
 * </p>
 */
@Slf4j
@Component
public class KafkaOutboxPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String topic;

    public KafkaOutboxPublisher(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${sigma.event.producer.topic:management-projection}") String topic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    /**
     * 트랜잭션 성공 후 이벤트를 직접 Kafka로 발행
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public <T extends EventPayload> void handleAfterCommit(DomainEvent<T> event) {
        log.info("[WORKAROUND] Publishing event to Kafka after commit. topic: {}, key: {}, type: {}",
                topic, event.getIdempotencyKey(), event.getEventType());
        try {
            kafkaTemplate.send(topic, event.getIdempotencyKey(), event);
        } catch (Exception e) {
            log.error("[WORKAROUND] Failed to publish event to Kafka after commit. key: {}", event.getIdempotencyKey(), e);
        }
    }
}
