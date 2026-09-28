package com.huy.jobpulse.email;

import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "jobpulse.email.enabled", havingValue = "true")
public class EmailScheduler {
    private static final Logger log = LoggerFactory.getLogger(EmailScheduler.class);
    private final DigestQueue queue;
    private final EmailProvider provider;
    private final EmailWebhooks webhooks;
    public EmailScheduler(DigestQueue queue, EmailProvider provider, EmailWebhooks webhooks) {
        this.queue = queue; this.provider = provider; this.webhooks = webhooks;
    }
    @Scheduled(scheduler = "emailTaskScheduler", fixedDelayString = "${jobpulse.email.poll-delay-ms:60000}",
            initialDelayString = "${jobpulse.email.initial-delay-ms:10000}")
    public void tick() {
        for (int i = 0; i < 100 && queue.prepareNext(); i++) { }
        for (int i = 0; i < 100 && webhooks.reconcileNext(); i++) { }
        for (int i = 0; i < 100; i++) {
            var next = queue.claimNext();
            if (next.isEmpty()) break;
            var claim = next.get();
            EmailProvider.Result result = provider.send(claim.payload(), claim.key());
            queue.finish(claim, result);
            if (result.outcome() != EmailProvider.Outcome.ACCEPTED)
                log.warn("Email digest {} attempt outcome {}", claim.id(), result.outcome());
        }
        for (int i = 0; i < 100 && webhooks.reconcileNext(); i++) { }
    }
}
