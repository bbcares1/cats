package sg.edu.nus.cats.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Private document vault configuration. Files are stored outside the web root
 * and are only reachable through an authorised controller.
 */
@ConfigurationProperties(prefix = "cats.storage")
public class StorageProperties {

    /** Root directory of the private vault. */
    private String root = "data/vault";

    /** Maximum accepted upload size in bytes (5 MB scheme limit). */
    private long maxFileSize = 5L * 1024 * 1024;

    public String getRoot() {
        return root;
    }

    public void setRoot(String root) {
        this.root = root;
    }

    public long getMaxFileSize() {
        return maxFileSize;
    }

    public void setMaxFileSize(long maxFileSize) {
        this.maxFileSize = maxFileSize;
    }
}
