package com.huy.jobpulse.ingestion.infrastructure;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class IngestionAmqpConfiguration {

    @Bean
    DirectExchange ingestionExchange() {
        return new DirectExchange(IngestionAmqpTopology.EXCHANGE, true, false);
    }

    @Bean
    DirectExchange ingestionDeadLetterExchange() {
        return new DirectExchange(
                IngestionAmqpTopology.DEAD_LETTER_EXCHANGE,
                true,
                false
        );
    }

    @Bean
    DirectExchange ingestionRetryExchange() {
        return new DirectExchange(IngestionAmqpTopology.RETRY_EXCHANGE, true, false);
    }

    @Bean
    Queue ingestionRetry5sQueue() {
        return retryQueue(IngestionAmqpTopology.RETRY_5S, 5_000);
    }

    @Bean
    Queue ingestionRetry30sQueue() {
        return retryQueue(IngestionAmqpTopology.RETRY_30S, 30_000);
    }

    private static Queue retryQueue(String name, int ttl) {
        return QueueBuilder.durable(name)
                .ttl(ttl)
                .deadLetterExchange(IngestionAmqpTopology.EXCHANGE)
                .deadLetterRoutingKey(IngestionAmqpTopology.ROUTING_KEY)
                .build();
    }

    @Bean
    Binding ingestionRetry5sBinding(Queue ingestionRetry5sQueue,
            DirectExchange ingestionRetryExchange) {
        return BindingBuilder.bind(ingestionRetry5sQueue)
                .to(ingestionRetryExchange).with(IngestionAmqpTopology.RETRY_5S);
    }

    @Bean
    Binding ingestionRetry30sBinding(Queue ingestionRetry30sQueue,
            DirectExchange ingestionRetryExchange) {
        return BindingBuilder.bind(ingestionRetry30sQueue)
                .to(ingestionRetryExchange).with(IngestionAmqpTopology.RETRY_30S);
    }

    @Bean
    Queue ingestionQueue() {
        return QueueBuilder.durable(IngestionAmqpTopology.QUEUE)
                .deadLetterExchange(
                        IngestionAmqpTopology.DEAD_LETTER_EXCHANGE
                )
                .deadLetterRoutingKey(
                        IngestionAmqpTopology.DEAD_LETTER_ROUTING_KEY
                )
                .build();
    }

    @Bean
    Queue ingestionDeadLetterQueue() {
        return QueueBuilder.durable(
                IngestionAmqpTopology.DEAD_LETTER_QUEUE
        ).build();
    }

    @Bean
    Binding ingestionBinding(
            Queue ingestionQueue,
            DirectExchange ingestionExchange
    ) {
        return BindingBuilder.bind(ingestionQueue)
                .to(ingestionExchange)
                .with(IngestionAmqpTopology.ROUTING_KEY);
    }

    @Bean
    Binding ingestionDeadLetterBinding(
            Queue ingestionDeadLetterQueue,
            DirectExchange ingestionDeadLetterExchange
    ) {
        return BindingBuilder.bind(ingestionDeadLetterQueue)
                .to(ingestionDeadLetterExchange)
                .with(IngestionAmqpTopology.DEAD_LETTER_ROUTING_KEY);
    }
}
