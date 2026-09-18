package com.huy.jobpulse.discovery.infrastructure;

import com.huy.jobpulse.discovery.application.CareersPageFetcher;
import com.huy.jobpulse.discovery.application.DiscoveryFetchException;
import com.huy.jobpulse.discovery.application.FetchedCareersPage;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;

@Component
public class SafeCareersPageFetcher implements CareersPageFetcher {

    private static final int MAX_REDIRECTS = 5;
    private static final int MAX_RESPONSE_BYTES = 1_000_000;
    private static final Set<Integer> REDIRECT_STATUSES = Set.of(
            301, 302, 303, 307, 308
    );

    private final HttpClient httpClient;

    public SafeCareersPageFetcher() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    }

    SafeCareersPageFetcher(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public FetchedCareersPage fetch(URI initialUri) {
        URI current = initialUri;
        for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
            validatePublicHttps(current);
            HttpResponse<InputStream> response = send(current);
            try (InputStream body = response.body()) {
                if (REDIRECT_STATUSES.contains(response.statusCode())) {
                    if (redirects == MAX_REDIRECTS) {
                        throw new DiscoveryFetchException(
                                "Careers page exceeded redirect limit"
                        );
                    }
                    String location = response.headers()
                            .firstValue("Location")
                            .orElseThrow(() -> new DiscoveryFetchException(
                                    "Redirect response is missing Location"
                            ));
                    current = current.resolve(location);
                    continue;
                }
                if (response.statusCode() < 200
                        || response.statusCode() >= 300) {
                    throw new DiscoveryFetchException(
                            "Careers page returned HTTP "
                                    + response.statusCode()
                    );
                }
                byte[] bytes = body.readNBytes(MAX_RESPONSE_BYTES + 1);
                if (bytes.length > MAX_RESPONSE_BYTES) {
                    throw new DiscoveryFetchException(
                            "Careers page exceeded 1000000-byte limit"
                    );
                }
                return new FetchedCareersPage(
                        current,
                        new String(bytes, StandardCharsets.UTF_8)
                );
            } catch (IOException exception) {
                throw new DiscoveryFetchException(
                        "Could not read careers page",
                        exception
                );
            }
        }
        throw new DiscoveryFetchException("Careers page redirect failed");
    }

    private HttpResponse<InputStream> send(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "text/html,application/xhtml+xml")
                .header("User-Agent", "JobPulse board discovery")
                .GET()
                .build();
        try {
            return httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofInputStream()
            );
        } catch (IOException exception) {
            throw new DiscoveryFetchException(
                    "Could not fetch careers page",
                    exception
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new DiscoveryFetchException(
                    "Careers page request was interrupted",
                    exception
            );
        }
    }

    static void validatePublicHttps(URI uri) {
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || uri.getHost() == null
                || uri.getUserInfo() != null) {
            throw new DiscoveryFetchException(
                    "Only HTTPS careers URLs without user info are allowed"
            );
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(uri.getHost());
        } catch (IOException exception) {
            throw new DiscoveryFetchException(
                    "Could not resolve careers page host",
                    exception
            );
        }
        if (addresses.length == 0) {
            throw new DiscoveryFetchException(
                    "Careers page host resolved to no addresses"
            );
        }
        for (InetAddress address : addresses) {
            if (!isPublic(address)) {
                throw new DiscoveryFetchException(
                        "Careers page resolves to a local or private address"
                );
            }
        }
    }

    private static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            return first != 0
                    && !(first == 100 && second >= 64 && second <= 127)
                    && !(first == 192 && second == 0)
                    && !(first >= 240);
        }
        if (address instanceof Inet6Address) {
            int first = Byte.toUnsignedInt(bytes[0]);
            return (first & 0xfe) != 0xfc;
        }
        return false;
    }
}
