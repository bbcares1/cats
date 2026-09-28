package sg.edu.nus.cats.service.mail;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import sg.edu.nus.cats.config.MailProperties;

@Component
@ConditionalOnProperty(name = "cats.mail.mode", havingValue = "smtp")
public class SmtpMailGateway implements MailGateway {

    private final JavaMailSender mailSender;
    private final MailProperties properties;

    public SmtpMailGateway(JavaMailSender mailSender, MailProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.getFrom());
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }

    @Override
    public String mode() {
        return "smtp";
    }
}
