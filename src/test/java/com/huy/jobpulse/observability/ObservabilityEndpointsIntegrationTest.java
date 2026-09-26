package com.huy.jobpulse.observability;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.core.env.Environment;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;

@SpringBootTest(properties = {
        "management.health.rabbit.enabled=false",
        "jobpulse.observability.backlog-initial-delay-ms=3600000"
})
@AutoConfigureMockMvc
@Testcontainers
class ObservabilityEndpointsIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Autowired MockMvc mvc;
    @Autowired BacklogGaugeUpdater gauges;
    @Autowired ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory;
    @Autowired Environment environment;

    @Test
    void exposesHealthProbesAndPrometheusWithoutExposingMetricsEndpoint() throws Exception {
        gauges.refresh();
        mvc.perform(get("/api/v1/jobs"))
                .andExpect(status().isOk());

        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/prometheus")
                        .with(httpBasic("metrics", "local-monitor-only")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "jobpulse_outbox_unpublished{application=\"jobpulse\"}")))
                .andExpect(content().string(containsString(
                        "jobpulse_search_duration_seconds_bucket")));
        mvc.perform(get("/actuator/metrics")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("monitor").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void brokerObservationsAndTracingAreConfigured() {
        assertThat(kafkaListenerContainerFactory.getContainerProperties()
                .isObservationEnabled()).isTrue();
        assertThat(environment.getProperty(
                "spring.rabbitmq.listener.simple.observation-enabled", Boolean.class))
                .isTrue();
        assertThat(environment.getProperty(
                "spring.rabbitmq.template.observation-enabled", Boolean.class))
                .isTrue();
        assertThat(environment.getProperty(
                "management.opentelemetry.tracing.export.otlp.endpoint"))
                .isEqualTo("http://localhost:4318/v1/traces");
    }
}
