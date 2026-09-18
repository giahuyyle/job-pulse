package com.huy.jobpulse.ingestion.infrastructure;

import java.util.UUID;

public record IngestionMessage(UUID requestId) {
}
