package com.huy.jobpulse.email;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.net.URI;
import java.util.*;

@ConfigurationProperties("jobpulse.email")
public record EmailProperties(boolean enabled, String apiKey, String webhookSecret,
        String from, String appUrl, String apiUrl, int dailyLimit, int monthlyLimit,
        Set<String> allowlist) {
    public EmailProperties {
        apiKey = apiKey == null ? "" : apiKey;
        webhookSecret = webhookSecret == null ? "" : webhookSecret;
        from = from == null ? "" : from;
        appUrl = appUrl == null ? "http://localhost:5173" : appUrl.replaceAll("/+$", "");
        apiUrl = apiUrl == null ? "https://api.resend.com" : apiUrl;
        dailyLimit = dailyLimit == 0 ? 100 : dailyLimit;
        monthlyLimit = monthlyLimit == 0 ? 3000 : monthlyLimit;
        if (dailyLimit < 1 || dailyLimit > 100 || monthlyLimit < 1 || monthlyLimit > 3000)
            throw new IllegalArgumentException("Email budgets must stay within the free tier");
        allowlist = allowlist == null ? Set.of() : Set.copyOf(allowlist);
        URI url = URI.create(appUrl);
        if (url.getHost() == null || !("https".equals(url.getScheme()) || "http".equals(url.getScheme())))
            throw new IllegalArgumentException("Email app URL must be HTTP(S)");
        if (enabled && (apiKey.isBlank() || webhookSecret.isBlank() || from.isBlank()
                || from.contains("\r") || from.contains("\n")))
            throw new IllegalArgumentException("Enabled email requires API key, webhook secret and sender");
    }
    public boolean allowed(String recipient) {
        return allowlist.isEmpty() || allowlist.stream().anyMatch(recipient::equalsIgnoreCase);
    }
}
