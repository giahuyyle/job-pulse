package com.huy.jobpulse.discovery.infrastructure;

import com.huy.jobpulse.discovery.application.DiscoveryFetchException;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SafeCareersPageFetcherTest {

    @Test
    void rejectsNonHttpsAndLocalOrPrivateAddresses() {
        assertRejected("http://example.com/careers");
        assertRejected("https://127.0.0.1/careers");
        assertRejected("https://10.0.0.4/careers");
        assertRejected("https://[fc00::1]/careers");
    }

    private static void assertRejected(String value) {
        assertThatThrownBy(() -> SafeCareersPageFetcher
                .validatePublicHttps(URI.create(value)))
                .isInstanceOf(DiscoveryFetchException.class);
    }
}
