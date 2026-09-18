package com.huy.jobpulse.analytics;

import com.huy.jobpulse.events.JobEventCodec;
import com.huy.jobpulse.events.KafkaTopics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class AnalyticsEventConsumer {

    private final JobEventCodec codec;
    private final AnalyticsEventHandler handler;

    public AnalyticsEventConsumer(
            JobEventCodec codec,
            AnalyticsEventHandler handler
    ) {
        this.codec = codec;
        this.handler = handler;
    }

    @KafkaListener(
            topics = KafkaTopics.JOB_EVENTS,
            groupId = AnalyticsEventHandler.CONSUMER_NAME
    )
    public void receive(String value) {
        handler.process(codec.decode(value));
    }
}
