package com.huy.jobpulse.ingestion.infrastructure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.huy.jobpulse.discovery.application.BoardVerification;
import com.huy.jobpulse.discovery.application.BoardVerifier;
import com.huy.jobpulse.ingestion.application.ExternalJob;
import com.huy.jobpulse.ingestion.application.JobSourceClient;
import com.huy.jobpulse.jobs.domain.JobSource;
import com.huy.jobpulse.jobs.domain.RemotePolicy;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class LeverJobSourceClient implements JobSourceClient, BoardVerifier {

    private static final String BASE_URL = "https://api.lever.co/v0/postings";
    private static final Pattern SITE = Pattern.compile("[A-Za-z0-9_-]{1,160}");

    private final RestClient restClient;

    public LeverJobSourceClient() {
        this(RestClient.builder()
                .baseUrl(BASE_URL)
                .requestFactory(requestFactory())
                .build());
    }

    LeverJobSourceClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public JobSource source() {
        return JobSource.LEVER;
    }

    @Override
    public void validateSourceAccount(String sourceAccount) {
        if (sourceAccount == null || !SITE.matcher(sourceAccount).matches()) {
            throw new IllegalArgumentException(
                    "Lever site must match [A-Za-z0-9_-]{1,160}"
            );
        }
    }

    @Override
    public BoardVerification verify(String sourceAccount) {
        validateSourceAccount(sourceAccount);
        try {
            fetchResponse(sourceAccount);
            return BoardVerification.verified();
        } catch (HttpClientErrorException.NotFound exception) {
            return BoardVerification.notFound("Lever board was not found");
        }
    }

    @Override
    public List<ExternalJob> fetchAll(String sourceAccount) {
        return fetchAll(sourceAccount, sourceAccount);
    }

    @Override
    public List<ExternalJob> fetchAll(
            String sourceAccount,
            String company
    ) {
        validateSourceAccount(sourceAccount);
        if (isBlank(company)) {
            throw new IllegalArgumentException("company must not be blank");
        }
        LeverJob[] jobs = fetchResponse(sourceAccount);
        validateJobs(jobs);
        return List.of(jobs).stream()
                .map(job -> new ExternalJob(
                        job.id(),
                        company.strip(),
                        job.text(),
                        job.categories() == null
                                ? null
                                : job.categories().location(),
                        job.descriptionPlain(),
                        job.categories() == null
                                ? null
                                : job.categories().commitment(),
                        remotePolicy(job.workplaceType()),
                        isBlank(job.applyUrl())
                                ? job.hostedUrl()
                                : job.applyUrl(),
                        job.createdAt() == null
                                ? null
                                : Instant.ofEpochMilli(job.createdAt())
                ))
                .toList();
    }

    private LeverJob[] fetchResponse(String sourceAccount) {
        LeverJob[] response = restClient.get()
                .uri("/{site}?mode=json", sourceAccount)
                .retrieve()
                .body(LeverJob[].class);
        if (response == null) {
            throw new IllegalStateException(
                    "Lever response is missing its postings array"
            );
        }
        return response;
    }

    private static void validateJobs(LeverJob[] jobs) {
        Set<String> ids = new HashSet<>();
        for (LeverJob job : jobs) {
            String url = job == null || isBlank(job.applyUrl())
                    ? job == null ? null : job.hostedUrl()
                    : job.applyUrl();
            if (job == null
                    || isBlank(job.id())
                    || isBlank(job.text())
                    || isBlank(url)) {
                throw new IllegalStateException(
                        "Lever response contains an invalid posting"
                );
            }
            if (!ids.add(job.id())) {
                throw new IllegalStateException(
                        "Lever response contains duplicate posting ID "
                                + job.id()
                );
            }
        }
    }

    private static RemotePolicy remotePolicy(String workplaceType) {
        if (workplaceType == null) {
            return RemotePolicy.UNSPECIFIED;
        }
        return switch (workplaceType.toLowerCase(Locale.ROOT)) {
            case "remote" -> RemotePolicy.REMOTE;
            case "hybrid" -> RemotePolicy.HYBRID;
            case "onsite", "on-site" -> RemotePolicy.ONSITE;
            default -> RemotePolicy.UNSPECIFIED;
        };
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static JdkClientHttpRequestFactory requestFactory() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(20));
        return requestFactory;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record LeverJob(
            String id,
            String text,
            String hostedUrl,
            String applyUrl,
            String descriptionPlain,
            Long createdAt,
            String workplaceType,
            Categories categories
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Categories(String location, String commitment) {
    }
}
