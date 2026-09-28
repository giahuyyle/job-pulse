package com.huy.jobpulse.email;

import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.*;

@Component
public class ResendEmailProvider implements EmailProvider {
    private final RestClient client;
    private final ObjectMapper mapper;
    public ResendEmailProvider(EmailProperties properties, ObjectMapper mapper) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        client = RestClient.builder().baseUrl(properties.apiUrl()).requestFactory(factory)
                .defaultHeader("Authorization", "Bearer " + properties.apiKey()).build();
        this.mapper = mapper;
    }
    @Override public Result send(String payload, String key) {
        try {
            return client.post().uri("/emails").contentType(MediaType.APPLICATION_JSON)
                    .header("Idempotency-Key", key).body(payload).exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        var json = mapper.readTree(response.getBody());
                        if (status >= 200 && status < 300) {
                            String id = json.path("id").asText("");
                            return id.isBlank() ? result(Outcome.UNKNOWN, "missing_provider_id", Duration.ZERO) : Result.accepted(id);
                        }
                        String code = json.path("name").asText("http_" + status);
                        Outcome outcome = switch (code) {
                            case "daily_quota_exceeded" -> Outcome.QUOTA_DAILY;
                            case "monthly_quota_exceeded" -> Outcome.QUOTA_MONTHLY;
                            case "rate_limit_exceeded", "concurrent_idempotent_requests", "resource_locked" -> Outcome.TRANSIENT;
                            default -> status >= 500 ? Outcome.UNKNOWN : status == 429 ? Outcome.TRANSIENT : Outcome.PERMANENT;
                        };
                        return result(outcome, code, retryAfter(response.getHeaders().getFirst("Retry-After")));
                    });
        } catch (RuntimeException exception) {
            // A timeout or malformed response may follow acceptance. Never log response bodies.
            return result(Outcome.UNKNOWN, "network_or_response_error", Duration.ZERO);
        }
    }
    private static Result result(Outcome outcome, String code, Duration delay) {
        return new Result(outcome, null, code.matches("[a-zA-Z0-9_]{1,80}") ? code : "provider_error", delay);
    }
    static Duration retryAfter(String header) {
        if (header == null) return Duration.ZERO;
        try { return Duration.ofSeconds(Math.max(0, Long.parseLong(header))); }
        catch (RuntimeException ignored) {
            try { return Duration.ofSeconds(Math.max(0, Duration.between(Instant.now(),
                    ZonedDateTime.parse(header, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).getSeconds())); }
            catch (RuntimeException invalid) { return Duration.ZERO; }
        }
    }
}
