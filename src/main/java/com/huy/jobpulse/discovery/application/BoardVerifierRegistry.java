package com.huy.jobpulse.discovery.application;

import com.huy.jobpulse.jobs.domain.JobSource;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class BoardVerifierRegistry {

    private final Map<JobSource, BoardVerifier> verifiers;

    public BoardVerifierRegistry(List<BoardVerifier> verifiers) {
        this.verifiers = new EnumMap<>(JobSource.class);
        for (BoardVerifier verifier : verifiers) {
            BoardVerifier previous = this.verifiers.put(
                    verifier.source(),
                    verifier
            );
            if (previous != null) {
                throw new IllegalStateException(
                        "Multiple board verifiers configured for "
                                + verifier.source()
                );
            }
        }
    }

    public BoardVerification verify(BoardCandidate candidate) {
        BoardVerifier verifier = verifiers.get(candidate.source());
        if (verifier == null) {
            throw new IllegalArgumentException(
                    "Discovery is not supported for " + candidate.source()
            );
        }
        return verifier.verify(candidate.sourceAccount());
    }
}
