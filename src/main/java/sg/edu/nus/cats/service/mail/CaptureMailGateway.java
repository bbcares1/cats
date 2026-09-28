package sg.edu.nus.cats.service.mail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import sg.edu.nus.cats.config.MailProperties;

/**
 * Demo transport: writes the message to a local directory instead of talking to
 * an SMTP server, so an offline demo still shows the notification trail.
 */
@Component
@ConditionalOnProperty(name = "cats.mail.mode", havingValue = "capture", matchIfMissing = true)
public class CaptureMailGateway implements MailGateway {

    private final MailProperties properties;
    private final Clock clock;

    public CaptureMailGateway(MailProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public void send(String to, String subject, String body) {
        Path directory = Path.of(properties.getCaptureDir());
        String fileName = "mail-" + Instant.now(clock).toEpochMilli() + "-" + Math.abs(to.hashCode()) + ".txt";
        String content = """
                To: %s
                From: %s
                Subject: %s

                %s
                """.formatted(to, properties.getFrom(), subject, body);
        try {
            Files.createDirectories(directory);
            Files.writeString(directory.resolve(fileName), content, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to capture mail for " + to, ex);
        }
    }

    @Override
    public String mode() {
        return "capture";
    }
}
