package com.huy.jobpulse.discovery.application;

import com.huy.jobpulse.jobs.domain.JobSource;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class BoardCandidateExtractor {

    private static final String GREENHOUSE = "boards.greenhouse.io";
    private static final String GREENHOUSE_NEW = "job-boards.greenhouse.io";
    private static final String LEVER = "jobs.lever.co";
    private static final String LEVER_EU = "jobs.eu.lever.co";
    private static final String ASHBY = "jobs.ashbyhq.com";

    public BoardCandidateExtraction extract(FetchedCareersPage page) {
        Document document = Jsoup.parse(
                page.html(),
                page.finalUri().toString()
        );
        List<URI> urls = new ArrayList<>();
        urls.add(page.finalUri());
        for (Element element : document.select("a[href], iframe[src]")) {
            String attribute = element.hasAttr("href") ? "href" : "src";
            String resolved = element.absUrl(attribute);
            if (resolved.isBlank()) {
                continue;
            }
            try {
                urls.add(URI.create(resolved));
            } catch (IllegalArgumentException ignored) {
                // A malformed link cannot be a board candidate.
            }
        }

        Map<String, BoardCandidate> candidates = new LinkedHashMap<>();
        List<URI> unsupported = new ArrayList<>();
        for (URI url : urls) {
            String host = normalizedHost(url);
            if (LEVER_EU.equals(host)) {
                unsupported.add(url);
                continue;
            }
            JobSource source = switch (host) {
                case GREENHOUSE, GREENHOUSE_NEW -> JobSource.GREENHOUSE;
                case LEVER -> JobSource.LEVER;
                case ASHBY -> JobSource.ASHBY;
                default -> null;
            };
            if (source == null) {
                continue;
            }
            String account = firstPathSegment(url);
            if (account == null) {
                continue;
            }
            String key = source + "/" + account.toLowerCase(Locale.ROOT);
            candidates.putIfAbsent(
                    key,
                    new BoardCandidate(source, account, url)
            );
        }
        return new BoardCandidateExtraction(
                List.copyOf(candidates.values()),
                List.copyOf(unsupported)
        );
    }

    private static String normalizedHost(URI uri) {
        return uri.getHost() == null
                ? ""
                : uri.getHost().toLowerCase(Locale.ROOT);
    }

    private static String firstPathSegment(URI uri) {
        String path = uri.getPath();
        if (path == null || path.isBlank() || "/".equals(path)) {
            return null;
        }
        for (String segment : path.split("/")) {
            if (!segment.isBlank()) {
                return segment;
            }
        }
        return null;
    }
}
