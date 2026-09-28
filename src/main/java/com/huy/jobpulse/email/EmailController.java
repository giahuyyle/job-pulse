package com.huy.jobpulse.email;

import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestController
public class EmailController {
    private final EmailPreferences preferences;
    private final WebhookVerifier verifier;
    private final EmailWebhooks webhooks;
    public EmailController(EmailPreferences preferences, WebhookVerifier verifier, EmailWebhooks webhooks) {
        this.preferences = preferences; this.verifier = verifier; this.webhooks = webhooks;
    }
    @GetMapping("/api/v1/email-settings")
    public EmailPreferences.SettingsView settings(@AuthenticationPrincipal OidcUser user) {
        return preferences.view(user.getSubject(), user.getEmail());
    }
    @GetMapping("/api/v1/email/unsubscribe")
    public Map<String, Boolean> valid(@RequestParam String token) { return Map.of("valid", preferences.validToken(token)); }

    @GetMapping(value = "/email/unsubscribe", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> confirmation(@RequestParam String token) {
        if (!preferences.validToken(token)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Link expired or invalid");
        String html = "<!doctype html><html><head><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>JobPulse email preferences</title></head>"
                + "<body style=\"font-family:Arial,sans-serif;background:#f5f6f2;color:#183f32;max-width:600px;margin:60px auto;padding:24px\">"
                + "<h1>Unsubscribe from job emails?</h1><p>Your saved searches and inbox alerts will stay available.</p>"
                + "<form method=\"post\" action=\"/api/v1/email/unsubscribe\"><input type=\"hidden\" name=\"token\" value=\"" + token
                + "\"><button type=\"submit\" style=\"padding:12px 18px\">Unsubscribe from all job emails</button></form></body></html>";
        return ResponseEntity.ok().header("Referrer-Policy", "no-referrer").header("Cache-Control", "no-store").body(html);
    }
    @PostMapping("/api/v1/email/unsubscribe")
    public ResponseEntity<String> unsubscribe(@RequestParam String token) {
        preferences.unsubscribe(token);
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).header("Referrer-Policy", "no-referrer")
                .header("Cache-Control", "no-store").body("<!doctype html><html><body><h1>Unsubscribed</h1><p>You can opt in again on the Alerts page.</p><a href=\"/alerts\">Return to JobPulse</a></body></html>");
    }
    @PostMapping("/api/v1/webhooks/resend")
    public ResponseEntity<Void> webhook(@RequestBody byte[] body,
            @RequestHeader(value = "svix-id", required = false) String id,
            @RequestHeader(value = "svix-timestamp", required = false) String timestamp,
            @RequestHeader(value = "svix-signature", required = false) String signature) {
        verifier.verify(body, id, timestamp, signature);
        webhooks.receive(id, body);
        return ResponseEntity.ok().build();
    }
}
