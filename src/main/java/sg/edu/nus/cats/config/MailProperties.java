package sg.edu.nus.cats.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cats.mail")
public class MailProperties {

    /** capture = write to the local capture directory, smtp = use JavaMail. */
    private String mode = "capture";

    /** Directory used when mode=capture. */
    private String captureDir = "data/mail";

    private String from = "no-reply@cats.local";

    private String baseUrl = "http://localhost:8080";

    /** Outbox worker configuration. */
    private boolean workerEnabled = true;

    private long workerIntervalMs = 15000L;

    private int maxAttempts = 5;

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getCaptureDir() {
        return captureDir;
    }

    public void setCaptureDir(String captureDir) {
        this.captureDir = captureDir;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public boolean isWorkerEnabled() {
        return workerEnabled;
    }

    public void setWorkerEnabled(boolean workerEnabled) {
        this.workerEnabled = workerEnabled;
    }

    public long getWorkerIntervalMs() {
        return workerIntervalMs;
    }

    public void setWorkerIntervalMs(long workerIntervalMs) {
        this.workerIntervalMs = workerIntervalMs;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }
}
