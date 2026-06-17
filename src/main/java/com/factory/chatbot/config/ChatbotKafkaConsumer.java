package com.factory.chatbot.config;

import com.factory.common.kafka.config.SigmaKafkaProperties;
import com.factory.common.kafka.consumer.CommonKafkaConsumer;
import com.factory.common.kafka.support.EventDispatcher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ChatbotKafkaConsumer extends CommonKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(ChatbotKafkaConsumer.class);

    private final ObjectMapper objectMapper;
    private final EventDispatcher eventDispatcher;
    private final List<String> allowedEvents;

    public ChatbotKafkaConsumer(ObjectMapper objectMapper, EventDispatcher eventDispatcher,
                                SigmaKafkaProperties properties) {
        super(objectMapper, eventDispatcher, properties.getConsumer().getSubscription());
        this.objectMapper = objectMapper;
        this.eventDispatcher = eventDispatcher;
        this.allowedEvents = properties.getConsumer().getSubscription();
    }

    @Override
    @KafkaListener(
            topics = "${sigma.event.consumer.topics}",
            groupId = "${sigma.event.consumer.groupId}"
    )
    public void consume(ConsumerRecord<String, String> record) {
        log.info("Received Kafka message in ChatbotKafkaConsumer: topic={}, partition={}, offset={}",
                record.topic(), record.partition(), record.offset());

        try {
            JsonNode rootNode = objectMapper.readTree(record.value());

            // Debezium wraps messages in {"schema": {...}, "payload": {...}} envelope.
            // Unwrap to the inner payload node if the envelope is present.
            JsonNode eventNode = rootNode;
            if (rootNode.has("schema") && rootNode.has("payload")) {
                eventNode = rootNode.path("payload");
                log.info("Debezium envelope detected in ChatbotKafkaConsumer – unwrapped to inner payload node");
            }

            JsonNode typeNode = eventNode.path("eventType");
            if (typeNode.isMissingNode() || typeNode.asText().isBlank()) {
                log.error("eventType must exist");
                throw new IllegalArgumentException("eventType must exist");
            }

            log.info("json node: {}", eventNode.toString());
            String eventType = typeNode.asText();

            if (!allowedEvents.contains(eventType)) {
                log.error("eventType not allowed: {}", eventType);
                return;
            }

            eventDispatcher.dispatch(eventNode);

        } catch (Exception e) {
            log.error("Failed to handle the event in ChatbotKafkaConsumer", e);
        }
    }
}
