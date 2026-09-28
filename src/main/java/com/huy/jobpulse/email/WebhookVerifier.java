package com.huy.jobpulse.email;

import org.springframework.stereotype.Component;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.*;

/** Svix v1 verification: https://docs.svix.com/receiving/verifying-payloads/how-manual */
@Component
public class WebhookVerifier {
    private final EmailProperties properties;
    private final Clock clock;
    public WebhookVerifier(EmailProperties properties, Clock clock) { this.properties = properties; this.clock = clock; }
    public void verify(byte[] body, String id, String timestamp, String signatures) {
        try {
            if (body.length > 65536 || id == null || id.length() > 255 || timestamp == null
                    || signatures == null || signatures.length() > 2048 || properties.webhookSecret().isBlank())
                throw new IllegalArgumentException();
            long seconds = Long.parseLong(timestamp);
            if (Math.abs(Math.subtractExact(clock.instant().getEpochSecond(), seconds)) > 300)
                throw new IllegalArgumentException();
            String secret = properties.webhookSecret();
            byte[] key = Base64.getDecoder().decode(secret.startsWith("whsec_") ? secret.substring(6) : secret);
            Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(key, "HmacSHA256"));
            mac.update((id + "." + timestamp + ".").getBytes(StandardCharsets.UTF_8));
            byte[] expected = mac.doFinal(body);
            for (String signature : signatures.split(" ")) {
                String[] parts = signature.split(",", 2);
                if (parts.length == 2 && "v1".equals(parts[0])) {
                    try { if (MessageDigest.isEqual(expected, Base64.getDecoder().decode(parts[1]))) return; }
                    catch (IllegalArgumentException ignored) { }
                }
            }
        } catch (Exception ignored) { }
        throw new IllegalArgumentException("Invalid webhook signature");
    }
}
