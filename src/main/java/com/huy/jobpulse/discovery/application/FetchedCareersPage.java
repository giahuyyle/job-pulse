package com.huy.jobpulse.discovery.application;

import java.net.URI;

public record FetchedCareersPage(URI finalUri, String html) {
}
