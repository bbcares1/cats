package sg.edu.nus.cats.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import sg.edu.nus.cats.config.MailProperties;
import sg.edu.nus.cats.service.mail.MailGateway;

@Component
public class OutboxWorker {

    private static final Logger log = LoggerFactory.getLogger(OutboxWorker.class);

    private final OutboxProcessor processor;
    private final MailProperties properties;
    private final MailGateway gateway;

    public OutboxWorker(OutboxProcessor processor, MailProperties properties, MailGateway gateway) {
        this.processor = processor;
        this.properties = properties;
        this.gateway = gateway;
    }

    @Scheduled(fixedDelayString = "${cats.mail.worker-interval-ms:15000}", initialDelayString = "10000")
    public void drain() {
        if (!properties.isWorkerEnabled()) {
            return;
        }
        List<Long> claimed = processor.claimBatch(20);
        if (claimed.isEmpty()) {
            return;
        }
        log.debug("Outbox worker ({}) delivering {} message(s)", gateway.mode(), claimed.size());
        claimed.forEach(processor::deliver);
    }
}
