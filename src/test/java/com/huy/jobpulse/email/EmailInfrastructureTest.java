package com.huy.jobpulse.email;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.net.InetSocketAddress;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

class EmailInfrastructureTest {
    private static final Instant NOW = Instant.parse("2026-09-28T13:00:00Z");
    private static final String SECRET = "whsec_" + Base64.getEncoder().encodeToString("test-secret-with-32-bytes-minimum!".getBytes(StandardCharsets.UTF_8));
    static EmailProperties properties(String url) {
        return new EmailProperties(false, "test-api-key", SECRET, "JobPulse <alerts@example.com>",
                "https://jobpulse.example", url, 100, 3000, Set.of());
    }
    public static String sign(String id, String timestamp, byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(Base64.getDecoder().decode(SECRET.substring(6)), "HmacSHA256"));
        mac.update((id + "." + timestamp + ".").getBytes(StandardCharsets.UTF_8));
        return "v1," + Base64.getEncoder().encodeToString(mac.doFinal(body));
    }
    @Test void rawBodySignatureAndTimestampAreVerified() throws Exception {
        var verifier = new WebhookVerifier(properties("http://localhost"), Clock.fixed(NOW, ZoneOffset.UTC));
        byte[] body = "{ \"type\":\"email.sent\" }".getBytes(StandardCharsets.UTF_8);
        String timestamp = Long.toString(NOW.getEpochSecond());
        verifier.verify(body, "event-1", timestamp, sign("event-1", timestamp, body));
        assertThatThrownBy(() -> verifier.verify("{}".getBytes(), "event-1", timestamp, sign("event-1", timestamp, body))).isInstanceOf(IllegalArgumentException.class);
        String old = Long.toString(NOW.minusSeconds(301).getEpochSecond());
        assertThatThrownBy(() -> verifier.verify(body, "event-1", old, sign("event-1", old, body))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> verifier.verify(body, null, timestamp, "bad")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rendererEscapesTextRejectsUnsafeUrlsAndCoversOverflow() {
        var mapper = JsonMapper.builder().build();
        var renderer = new DigestRenderer(properties("http://localhost"), mapper);
        var jobs = new ArrayList<DigestRenderer.Job>();
        for (int i = 0; i < 51; i++) jobs.add(new DigestRenderer.Job(UUID.randomUUID(), "<script>title</script>", "A&B", null,
                "REMOTE", i == 0 ? "javascript:alert(1)" : "https://example.com/role?a=1&b=2", Set.of("<Backend>")));
        var json = mapper.readTree(renderer.render(jobs, "person@example.com", "a".repeat(43), UUID.randomUUID()));
        assertThat(json.path("html").asText()).doesNotContain("<script>", "javascript:")
                .contains("&lt;script&gt;", "A&amp;B", "Showing 50 of 51", "Unsubscribe from all");
        assertThat(json.path("text").asText()).contains("<Backend>", "Showing 50 of 51");
        assertThat(json.path("headers").path("List-Unsubscribe-Post").asText()).isEqualTo("List-Unsubscribe=One-Click");
    }
    @Test void writesRepresentativeHtmlAndTextPreview() throws Exception {
        var mapper = JsonMapper.builder().build();
        var renderer = new DigestRenderer(properties("http://localhost"), mapper);
        var jobs = List.of(
                new DigestRenderer.Job(UUID.randomUUID(), "Senior Backend Engineer", "Example Labs", "Remote, United States", "REMOTE", "https://example.com/jobs/backend", Set.of("Remote backend", "Platform engineering")),
                new DigestRenderer.Job(UUID.randomUUID(), "Platform Engineer", "Example Systems", "New York, NY", "HYBRID", "https://example.com/jobs/platform", Set.of("Platform engineering")));
        var json = mapper.readTree(renderer.render(jobs, "preview@example.com", "a".repeat(43), UUID.randomUUID()));
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/email-preview.html"), json.path("html").asText());
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/email-preview.txt"), json.path("text").asText());
    }
    @Test void resendAdapterSendsFrozenJsonAndClassifiesProviderErrors() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> error = new AtomicReference<>();
        AtomicReference<String> key = new AtomicReference<>();
        AtomicReference<String> payload = new AtomicReference<>();
        server.createContext("/emails", exchange -> {
            key.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            payload.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String response = error.get() == null ? "{\"id\":\"provider-123\"}" : "{\"name\":\"" + error.get() + "\"}";
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.getResponseHeaders().add("Retry-After", "600");
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(error.get() == null ? 200 : 429, bytes.length);
            exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        try {
            var provider = new ResendEmailProvider(properties("http://127.0.0.1:" + server.getAddress().getPort()), JsonMapper.builder().build());
            assertThat(provider.send("{\"subject\":\"frozen\"}", "stable-key").outcome()).isEqualTo(EmailProvider.Outcome.ACCEPTED);
            assertThat(key.get()).isEqualTo("stable-key"); assertThat(payload.get()).isEqualTo("{\"subject\":\"frozen\"}");
            error.set("monthly_quota_exceeded");
            assertThat(provider.send("{}", "next-key").outcome()).isEqualTo(EmailProvider.Outcome.QUOTA_MONTHLY);
            error.set("rate_limit_exceeded");
            assertThat(provider.send("{}", "next-key").retryAfter()).isEqualTo(Duration.ofMinutes(10));
        } finally { server.stop(0); }
    }
}
