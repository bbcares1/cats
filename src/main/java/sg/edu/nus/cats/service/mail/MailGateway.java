package sg.edu.nus.cats.service.mail;

public interface MailGateway {

    void send(String to, String subject, String body);

    String mode();
}
