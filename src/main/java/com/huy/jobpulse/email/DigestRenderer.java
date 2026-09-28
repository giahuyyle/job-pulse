package com.huy.jobpulse.email;

import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.*;

@Component
public class DigestRenderer {
    private final EmailProperties properties;
    private final ObjectMapper mapper;
    public DigestRenderer(EmailProperties properties, ObjectMapper mapper) { this.properties = properties; this.mapper = mapper; }
    public String render(List<Job> jobs, String recipient, String token, UUID digestId) {
        String inbox = properties.appUrl() + "/alerts";
        String unsubscribe = properties.appUrl() + "/email/unsubscribe?token=" + token;
        String oneClick = properties.appUrl() + "/api/v1/email/unsubscribe?token=" + token;
        String subject = "JobPulse: " + jobs.size() + " new jobs matching your searches.";
        StringBuilder text = new StringBuilder(subject + "\nDaily at 9:00 AM Eastern Time.\n\n");
        StringBuilder html = new StringBuilder("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>JobPulse daily job digest</title></head><body style=\"margin:0;background:#f5f6f2;color:#183f32;font-family:Arial,sans-serif\"><main style=\"max-width:640px;margin:auto;padding:28px\"><h1>" + escape(subject) + "</h1><p>Daily at 9:00 AM Eastern Time.</p>");
        for (Job job : jobs.stream().limit(50).toList()) {
            String names = String.join(", ", job.searchNames());
            String url = safeUrl(job.applyUrl());
            String location = job.location() == null ? "Location flexible" : job.location();
            text.append(job.title()).append("\n").append(job.company()).append(" · ").append(location)
                    .append(" · ").append(job.remotePolicy()).append("\nMatches: ").append(names).append("\n");
            if (url != null) text.append("View role: ").append(url).append("\n");
            text.append("\n");
            html.append("<article style=\"padding:20px;background:white;margin:16px 0;border-radius:8px\"><h2>")
                    .append(escape(job.title())).append("</h2><p>").append(escape(job.company())).append(" · ")
                    .append(escape(location)).append(" · ").append(escape(job.remotePolicy())).append("</p><p>Matches: ")
                    .append(escape(names)).append("</p>");
            if (url != null) html.append("<a href=\"").append(escape(url)).append("\">View role</a>");
            html.append("</article>");
        }
        if (jobs.size() > 50) {
            String overflow = "Showing 50 of " + jobs.size() + " jobs. View all matches in your Alerts inbox.";
            text.append(overflow).append("\n\n"); html.append("<p>").append(overflow).append("</p>");
        }
        text.append("Your alerts / Manage preferences: ").append(inbox)
                .append("\nUnsubscribe from all job emails: ").append(unsubscribe).append("\n");
        html.append("<p><a href=\"").append(escape(inbox)).append("\">Your alerts / Manage preferences</a></p><p><a href=\"")
                .append(escape(unsubscribe)).append("\">Unsubscribe from all job emails</a></p></main></body></html>");
        return mapper.writeValueAsString(Map.of("from", properties.from(), "to", List.of(recipient),
                "subject", subject, "html", html.toString(), "text", text.toString(),
                "headers", Map.of("List-Unsubscribe", "<" + oneClick + ">", "List-Unsubscribe-Post", "List-Unsubscribe=One-Click"),
                "tags", List.of(Map.of("name", "digest_id", "value", digestId.toString()))));
    }
    static String safeUrl(String input) {
        try {
            URI url = URI.create(input);
            return url.getHost() != null && url.getUserInfo() == null
                    && ("https".equalsIgnoreCase(url.getScheme()) || "http".equalsIgnoreCase(url.getScheme())) ? input : null;
        } catch (RuntimeException exception) { return null; }
    }
    static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
    public record Job(UUID id, String title, String company, String location, String remotePolicy,
            String applyUrl, Set<String> searchNames) {}
}
