package com.huy.jobpulse.observability;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityProfileTest {

    @Test
    void observabilityProfileEnablesEcsJsonConsoleLogging() throws Exception {
        var sources = new YamlPropertySourceLoader().load(
                "observability", new ClassPathResource("application-observability.yml"));

        assertThat(sources).singleElement().satisfies(source ->
                assertThat(source.getProperty("logging.structured.format.console"))
                        .isEqualTo("ecs"));
    }
}
