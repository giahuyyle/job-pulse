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

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class AshbyJobSourceClient implements JobSourceClient, BoardVerifier {

    private static final String BASE_URL =
            "https://api.ashbyhq.com/posting-api/job-board";
    private static final Pattern BOARD_NAME =
            Pattern.compile("[A-Za-z0-9_-]{1,160}");

    private final RestClient restClient;

    public AshbyJobSourceClient() {
        this(RestClient.builder()
                .baseUrl(BASE_URL)
                .requestFactory(requestFactory())
                .build());
    }

    AshbyJobSourceClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public JobSource source() {
        return JobSource.ASHBY;
    }

    @Override
    public void validateSourceAccount(String sourceAccount) {
        if (sourceAccount == null
                || !BOARD_NAME.matcher(sourceAccount).matches()) {
            throw new IllegalArgumentException(
                    "Ashby board name must match [A-Za-z0-9_-]{1,160}"
            );
        }
    }

    @Override
    public BoardVerification verify(String sourceAccount) {
        validateSourceAccount(sourceAccount);
        try {
            fetchAndMap(sourceAccount, sourceAccount);
            return BoardVerification.verified();
        } catch (HttpClientErrorException.NotFound exception) {
            return BoardVerification.notFound("Ashby board was not found");
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
        return fetchAndMap(sourceAccount, company.strip());
    }

    private List<ExternalJob> fetchAndMap(
            String sourceAccount,
            String company
    ) {
        AshbyResponse response = restClient.get()
                .uri("/{boardName}", sourceAccount)
                .retrieve()
                .body(AshbyResponse.class);
        if (response == null || response.jobs() == null) {
            throw new IllegalStateException(
                    "Ashby response is missing its jobs array"
            );
        }

        Set<String> sourceJobIds = new HashSet<>();
        return response.jobs().stream()
                .filter(AshbyJobSourceClient::isListed)
                .map(job -> map(job, company, sourceJobIds))
                .toList();
    }

    private static boolean isListed(AshbyJob job) {
        if (job == null) {
            throw new IllegalStateException(
                    "Ashby response contains a null posting"
            );
        }
        if (job.isListed() == null) {
            throw new IllegalStateException(
                    "Ashby posting is missing isListed"
            );
        }
        return job.isListed();
    }

    private static ExternalJob map(
            AshbyJob job,
            String company,
            Set<String> sourceJobIds
    ) {
        String sourceJobId = jobId(job.jobUrl());
        if (!sourceJobIds.add(sourceJobId)) {
            throw new IllegalStateException(
                    "Ashby response contains duplicate posting ID "
                            + sourceJobId
            );
        }
        if (isBlank(job.title()) || isBlank(job.applyUrl())) {
            throw new IllegalStateException(
                    "Ashby response contains an invalid posting"
            );
        }
        return new ExternalJob(
                sourceJobId,
                company,
                job.title(),
                job.location(),
                job.descriptionPlain(),
                job.employmentType(),
                remotePolicy(job.workplaceType()),
                job.applyUrl(),
                publishedAt(job.publishedAt())
        );
    }

    private static String jobId(String jobUrl) {
        if (isBlank(jobUrl)) {
            throw new IllegalStateException("Ashby posting is missing jobUrl");
        }
        URI uri;
        try {
            uri = URI.create(jobUrl);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "Ashby posting has a malformed jobUrl",
                    exception
            );
        }
        String path = uri.getRawPath();
        if (!uri.isAbsolute()
                || uri.getHost() == null
                || path == null
                || path.isBlank()
                || "/".equals(path)) {
            throw new IllegalStateException(
                    "Ashby posting has a malformed jobUrl"
            );
        }
        String normalized = path.startsWith("/")
                ? path.substring(1)
                : path;
        if (normalized.isBlank() || normalized.length() > 255) {
            throw new IllegalStateException(
                    "Ashby posting has an invalid job URL path"
            );
        }
        return normalized;
    }

    private static Instant publishedAt(String value) {
        if (isBlank(value)) {
            throw new IllegalStateException(
                    "Ashby posting is missing publishedAt"
            );
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IllegalStateException(
                    "Ashby posting has an invalid publishedAt",
                    exception
            );
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
        requestFactory.setReadTimeout(Duration.ofSeconds(30));
        return requestFactory;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AshbyResponse(List<AshbyJob> jobs) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AshbyJob(
            String title,
            String location,
            String descriptionPlain,
            String employmentType,
            String workplaceType,
            String jobUrl,
            String applyUrl,
            String publishedAt,
            Boolean isListed
    ) {
    }
}
