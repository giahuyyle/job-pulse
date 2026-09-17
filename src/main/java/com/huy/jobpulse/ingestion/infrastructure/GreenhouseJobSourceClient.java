package com.huy.jobpulse.ingestion.infrastructure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.huy.jobpulse.ingestion.application.ExternalJob;
import com.huy.jobpulse.ingestion.application.JobSourceClient;
import com.huy.jobpulse.jobs.domain.JobSource;
import com.huy.jobpulse.jobs.domain.RemotePolicy;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

@Component
public class GreenhouseJobSourceClient implements JobSourceClient {

    private static final String BASE_URL =
            "https://boards-api.greenhouse.io/v1/boards";
    private static final Pattern BOARD_TOKEN =
            Pattern.compile("[A-Za-z0-9_-]{1,160}");

    private final RestClient restClient;
    private final ConcurrentMap<String, String> boardNames =
            new ConcurrentHashMap<>();

    public GreenhouseJobSourceClient() {
        this(RestClient.builder()
                .baseUrl(BASE_URL)
                .requestFactory(requestFactory())
                .build());
    }

    GreenhouseJobSourceClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public JobSource source() {
        return JobSource.GREENHOUSE;
    }

    @Override
    public List<ExternalJob> fetchAll(String sourceAccount) {
        validateSourceAccount(sourceAccount);

        String company = boardNames.computeIfAbsent(
                sourceAccount,
                this::fetchBoardName
        );
        JobsResponse response = restClient.get()
                .uri("/{boardToken}/jobs?content=true", sourceAccount)
                .retrieve()
                .body(JobsResponse.class);

        validateJobsResponse(response);

        return response.jobs().stream()
                .map(job -> new ExternalJob(
                        Long.toString(job.id()),
                        company,
                        job.title(),
                        job.location() == null
                                ? null
                                : job.location().name(),
                        job.content(),
                        null,
                        RemotePolicy.UNSPECIFIED,
                        job.absoluteUrl(),
                        null
                ))
                .toList();
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

    @Override
    public void validateSourceAccount(String sourceAccount) {
        if (sourceAccount == null
                || !BOARD_TOKEN.matcher(sourceAccount).matches()) {
            throw new IllegalArgumentException(
                    "Greenhouse board token must match [A-Za-z0-9_-]{1,160}"
            );
        }
    }

    private String fetchBoardName(String sourceAccount) {
        BoardResponse board = restClient.get()
                .uri("/{boardToken}", sourceAccount)
                .retrieve()
                .body(BoardResponse.class);
        if (board == null || isBlank(board.name())) {
            throw new IllegalStateException(
                    "Greenhouse board response is missing its name"
            );
        }
        return board.name().strip();
    }

    private static void validateJobsResponse(JobsResponse response) {
        if (response == null || response.jobs() == null) {
            throw new IllegalStateException(
                    "Greenhouse jobs response is missing its jobs array"
            );
        }

        Set<Long> postingIds = new HashSet<>();
        for (GreenhouseJob job : response.jobs()) {
            if (job == null
                    || job.id() <= 0
                    || isBlank(job.title())
                    || isBlank(job.absoluteUrl())) {
                throw new IllegalStateException(
                        "Greenhouse jobs response contains an invalid posting"
                );
            }
            if (!postingIds.add(job.id())) {
                throw new IllegalStateException(
                        "Greenhouse jobs response contains duplicate posting ID "
                                + job.id()
                );
            }
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record BoardResponse(String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record JobsResponse(List<GreenhouseJob> jobs) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GreenhouseJob(
            long id,
            String title,
            @JsonProperty("absolute_url") String absoluteUrl,
            Location location,
            String content
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Location(String name) {
    }
}
