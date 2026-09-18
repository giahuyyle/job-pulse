package com.huy.jobpulse.ingestion.application;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IngestionDispatcherTest {

    @Test
    void marksPublishedOnlyAfterConfirmedPublishReturns() {
        IngestionRequestService requests = mock(IngestionRequestService.class);
        IngestionPublisher publisher = mock(IngestionPublisher.class);
        UUID requestId = UUID.randomUUID();
        when(requests.findUnpublishedIds()).thenReturn(List.of(requestId));

        new IngestionDispatcher(requests, publisher).dispatchUnpublished();

        verify(publisher).publish(requestId);
        verify(requests).markPublished(requestId);
    }

    @Test
    void leavesRequestUnpublishedWhenBrokerPublishFails() {
        IngestionRequestService requests = mock(IngestionRequestService.class);
        IngestionPublisher publisher = mock(IngestionPublisher.class);
        UUID requestId = UUID.randomUUID();
        when(requests.findUnpublishedIds()).thenReturn(List.of(requestId));
        doThrow(new IllegalStateException("broker unavailable"))
                .when(publisher).publish(requestId);

        new IngestionDispatcher(requests, publisher).dispatchUnpublished();

        verify(requests, never()).markPublished(requestId);
    }
}
