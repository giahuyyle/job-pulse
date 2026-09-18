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
