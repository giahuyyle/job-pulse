package com.huy.jobpulse.ingestion.infrastructure;

import com.huy.jobpulse.ingestion.application.IngestionPublisher;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

@Component
public class RabbitIngestionPublisher implements IngestionPublisher {

    private static final long CONFIRM_TIMEOUT_MILLIS = 5_000;

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public RabbitIngestionPublisher(
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(UUID requestId) {
        send(requestId, IngestionAmqpTopology.EXCHANGE, IngestionAmqpTopology.ROUTING_KEY);
    }

    @Override
    public void publishRetry(UUID requestId, int completedAttempts) {
        String queue = completedAttempts <= 1
                ? IngestionAmqpTopology.RETRY_5S : IngestionAmqpTopology.RETRY_30S;
        send(requestId, IngestionAmqpTopology.RETRY_EXCHANGE, queue);
    }

    private void send(UUID requestId, String exchange, String routingKey) {
        Message message = MessageBuilder
                .withBody(serialize(new IngestionMessage(requestId)))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setMessageId(requestId.toString())
                .build();
        rabbitTemplate.invoke(operations -> {
            operations.send(exchange, routingKey, message);
            operations.waitForConfirmsOrDie(CONFIRM_TIMEOUT_MILLIS);
            return null;
        });
    }

    private byte[] serialize(IngestionMessage message) {
        try {
            return objectMapper.writeValueAsBytes(message);
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "Could not serialize ingestion request",
                    exception
            );
        }
    }
}
