package com.huy.jobpulse.discovery.application;

import java.net.URI;
import java.util.List;

public record BoardCandidateExtraction(
        List<BoardCandidate> candidates,
        List<URI> unsupportedBoardUrls
) {
}
