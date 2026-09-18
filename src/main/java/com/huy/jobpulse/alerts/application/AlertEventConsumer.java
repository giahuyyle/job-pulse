package com.huy.jobpulse.alerts.application;

import com.huy.jobpulse.events.JobEventCodec;
import com.huy.jobpulse.events.KafkaTopics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class AlertEventConsumer {

    private final JobEventCodec codec;
    private final AlertEventHandler handler;

    public AlertEventConsumer(
            JobEventCodec codec,
            AlertEventHandler handler
    ) {
        this.codec = codec;
        this.handler = handler;
    }

    @KafkaListener(
            topics = KafkaTopics.JOB_EVENTS,
            groupId = AlertEventHandler.CONSUMER_NAME
    )
    public void receive(String value) {
        handler.process(codec.decode(value));
    }
}
