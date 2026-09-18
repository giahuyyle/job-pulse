package com.huy.jobpulse.discovery.application;

import java.net.URI;

public interface CareersPageFetcher {

    FetchedCareersPage fetch(URI uri);
}
