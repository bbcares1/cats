package sg.edu.nus.cats.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import sg.edu.nus.cats.domain.enums.DocumentType;

/** Metadata for one private attachment. The bytes live outside the database. */
@Entity
@Table(name = "claim_document", uniqueConstraints = @UniqueConstraint(name = "uk_claim_document_revision",
        columnNames = { "claim_id", "claim_revision", "document_type" }))
public class ClaimDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false)
    private CourseClaim claim;

    @Column(name = "claim_revision", nullable = false)
    private int claimRevision;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 24)
    private DocumentType documentType;

    @Column(name = "storage_key", nullable = false, unique = true, length = 200)
    private String storageKey;

    @Column(name = "original_name", nullable = false, length = 200)
    private String originalName;

    @Column(name = "detected_content_type", nullable = false, length = 100)
    private String detectedContentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by", nullable = false)
    private Employee uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public CourseClaim getClaim() {
        return claim;
    }

    public void setClaim(CourseClaim claim) {
        this.claim = claim;
    }

    public int getClaimRevision() {
        return claimRevision;
    }

    public void setClaimRevision(int claimRevision) {
        this.claimRevision = claimRevision;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public void setDocumentType(DocumentType documentType) {
        this.documentType = documentType;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public void setStorageKey(String storageKey) {
        this.storageKey = storageKey;
    }

    public String getOriginalName() {
        return originalName;
    }

    public void setOriginalName(String originalName) {
        this.originalName = originalName;
    }

    public String getDetectedContentType() {
        return detectedContentType;
    }

    public void setDetectedContentType(String detectedContentType) {
        this.detectedContentType = detectedContentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public Employee getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(Employee uploadedBy) {
        this.uploadedBy = uploadedBy;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(Instant uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public String getSizeLabel() {
        return String.format("%.1f KB", sizeBytes / 1024.0);
    }
}
