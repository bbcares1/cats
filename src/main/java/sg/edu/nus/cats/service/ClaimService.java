package sg.edu.nus.cats.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import sg.edu.nus.cats.domain.AuditEvent;
import sg.edu.nus.cats.domain.ClaimDocument;
import sg.edu.nus.cats.domain.CourseApplication;
import sg.edu.nus.cats.domain.CourseClaim;
import sg.edu.nus.cats.domain.Employee;
import sg.edu.nus.cats.domain.enums.AggregateType;
import sg.edu.nus.cats.domain.enums.ApplicationStatus;
import sg.edu.nus.cats.domain.enums.AuditEventType;
import sg.edu.nus.cats.domain.enums.ClaimStatus;
import sg.edu.nus.cats.domain.enums.DocumentType;
import sg.edu.nus.cats.domain.enums.LedgerEntryType;
import sg.edu.nus.cats.domain.enums.OutboxTemplateCode;
import sg.edu.nus.cats.dto.ClaimForm;
import sg.edu.nus.cats.dto.ViewMapper;
import sg.edu.nus.cats.repository.ApprovalAssignmentRepository;
import sg.edu.nus.cats.repository.ClaimDocumentRepository;
import sg.edu.nus.cats.repository.CourseApplicationRepository;
import sg.edu.nus.cats.repository.CourseClaimRepository;
import sg.edu.nus.cats.repository.EmployeeRepository;
import sg.edu.nus.cats.support.BusinessException;
import sg.edu.nus.cats.support.ErrorCode;
import sg.edu.nus.cats.support.Json;

/** Fee claim workflow: submit with documents, decide, revise after rejection, reimburse. */
@Service
public class ClaimService {

    private final CourseClaimRepository claims;
    private final ClaimDocumentRepository documents;
    private final CourseApplicationRepository applications;
    private final EmployeeRepository employees;
    private final ApprovalAssignmentRepository assignments;
    private final EntitlementService entitlements;
    private final LedgerService ledger;
    private final AuditService audit;
    private final OutboxService outbox;
    private final DocumentStorageService storage;
    private final Clock clock;

    public ClaimService(CourseClaimRepository claims, ClaimDocumentRepository documents,
            CourseApplicationRepository applications, EmployeeRepository employees,
            ApprovalAssignmentRepository assignments, EntitlementService entitlements, LedgerService ledger,
            AuditService audit, OutboxService outbox, DocumentStorageService storage, Clock clock) {
        this.claims = claims;
        this.documents = documents;
        this.applications = applications;
        this.employees = employees;
        this.assignments = assignments;
        this.entitlements = entitlements;
        this.ledger = ledger;
        this.audit = audit;
        this.outbox = outbox;
        this.storage = storage;
        this.clock = clock;
    }

    @Transactional
    public CourseClaim submit(Long employeeId, ClaimForm form, List<MultipartFile> receipts,
            List<MultipartFile> certificates) {
        Employee employee = requireEmployee(employeeId);
        CourseApplication application = applications.findById(form.getApplicationId())
                .orElseThrow(() -> BusinessException.notFound("Application"));
        if (!application.getEmployee().getId().equals(employeeId)) {
            throw BusinessException.accessDenied("This application belongs to another employee.");
        }
        assertClaimable(application);
        if (claims.findByApplicationId(application.getId()).isPresent()) {
            throw new BusinessException(ErrorCode.CLAIM_ALREADY_EXISTS,
                    "A claim already exists for " + application.getReferenceNo() + ".");
        }
        BigDecimal amount = requireValidAmount(form.getAmount(), application);
        Employee approver = resolveApprover(employee, form.getApproverId());
        requireDocuments(receipts, "Upload at least one receipt for the course fee.");

        Instant now = Instant.now(clock);
        CourseClaim claim = new CourseClaim();
        claim.setApplication(application);
        claim.setRevision(1);
        claim.setAmount(amount);
        claim.setPaidByEmployee(form.isPaidByEmployee());
        claim.setApprover(approver);
        claim.setStatus(ClaimStatus.SUBMITTED);
        claim.setSubmittedAt(now);
        CourseClaim saved = claims.saveAndFlush(claim);

        List<String> storedKeys = storeDocuments(saved, receipts, DocumentType.RECEIPT, employee, now);
        storedKeys.addAll(storeDocuments(saved, certificates, DocumentType.COMPLETION_CERTIFICATE, employee, now));

        AuditEvent event = audit.record(AggregateType.CLAIM, auditKey(saved), AuditEventType.CLAIM_SUBMITTED, employee, null,
                ClaimStatus.SUBMITTED.name(), null, snapshot(saved, storedKeys.size()));
        notifyApprover(event, saved, approver);
        return saved;
    }

    @Transactional
    public CourseClaim revise(Long employeeId, Long claimId, ClaimForm form, List<MultipartFile> receipts,
            List<MultipartFile> certificates) {
        Employee employee = requireEmployee(employeeId);
        CourseClaim claim = requireOwned(employeeId, claimId);
        if (!claim.getStatus().isRevisable()) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Only a rejected claim can be revised. This claim is " + claim.getStatus().getDisplayName()
                            + ".");
        }
        if (form.getVersion() != null && form.getVersion() != claim.getVersion()) {
            throw new BusinessException(ErrorCode.STALE_VERSION,
                    "Someone else changed this claim. Reload the page and try again.");
        }
        BigDecimal amount = requireValidAmount(form.getAmount(), claim.getApplication());
        Employee approver = resolveApprover(employee, form.getApproverId());

        int newRevision = claim.getRevision() + 1;
        requireDocumentsOrExistingReceipt(claim, newRevision, receipts);

        Instant now = Instant.now(clock);
        claim.submitRevision(amount, form.isPaidByEmployee(), approver, now);
        claim.bumpRevision();
        CourseClaim saved = claims.saveAndFlush(claim);

        int added = storeDocuments(saved, receipts, DocumentType.RECEIPT, employee, now).size();
        added += storeDocuments(saved, certificates, DocumentType.COMPLETION_CERTIFICATE, employee, now).size();

        AuditEvent event = audit.record(AggregateType.CLAIM, auditKey(saved), AuditEventType.CLAIM_RESUBMITTED, employee,
                ClaimStatus.REJECTED.name(), ClaimStatus.SUBMITTED.name(), null, snapshot(saved, added));
        notifyApprover(event, saved, approver);
        return saved;
    }

    @Transactional
    public CourseClaim decide(Long managerId, Long claimId, boolean approved, String reason, Long expectedVersion) {
        Employee manager = requireEmployee(managerId);
        CourseClaim claim = claims.findWithDetailsById(claimId)
                .orElseThrow(() -> BusinessException.notFound("Claim"));
        if (claim.getApprover() == null || !claim.getApprover().getId().equals(managerId)) {
            throw BusinessException.accessDenied("You are not the approving manager for this claim.");
        }
        if (claim.getApplication().getEmployee().getId().equals(managerId)) {
            throw BusinessException.accessDenied("You cannot decide your own fee claim.");
        }
        if (!claim.getStatus().isPendingReview()) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "This claim is already " + claim.getStatus().getDisplayName().toLowerCase()
                            + " and cannot be decided again.");
        }
        if (expectedVersion != null && expectedVersion != claim.getVersion()) {
            throw new BusinessException(ErrorCode.STALE_VERSION,
                    "This claim was changed after you opened it. Reload the queue and decide again.");
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "A reason is required for every decision.");
        }

        Instant now = Instant.now(clock);
        AuditEvent event = audit.record(AggregateType.CLAIM, auditKey(claim),
                approved ? AuditEventType.CLAIM_APPROVED : AuditEventType.CLAIM_REJECTED, manager,
                claim.getStatus().name(), approved ? ClaimStatus.APPROVED.name() : ClaimStatus.REJECTED.name(), reason,
                snapshot(claim, claim.getDocumentsForCurrentRevision().size()));
        claim.decide(approved, manager, reason, now);
        CourseClaim saved = claims.saveAndFlush(claim);

        outbox.enqueue(event, saved.getApplication().getEmployee(),
                approved ? OutboxTemplateCode.CLAIM_APPROVED : OutboxTemplateCode.CLAIM_REJECTED,
                Map.of("reference", saved.getApplication().getReferenceNo(),
                        "claimId", saved.getId(),
                        "applicationId", saved.getApplication().getId(),
                        "recipientName", saved.getApplication().getEmployee().getFullName(),
                        "reason", reason,
                        "summary", (approved ? "Approved" : "Rejected") + " your fee claim of $"
                                + ViewMapper.money(saved.getAmount()) + " for " + saved.getApplication().getCourseTitle()
                                + "."));
        return saved;
    }

    @Transactional
    public CourseClaim reimburse(Long adminId, Long claimId, String reference, Long expectedVersion) {
        Employee admin = requireEmployee(adminId);
        CourseClaim claim = claims.findWithDetailsById(claimId)
                .orElseThrow(() -> BusinessException.notFound("Claim"));
        if (claim.getStatus() != ClaimStatus.APPROVED) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Only an approved claim can be reimbursed. This claim is " + claim.getStatus().getDisplayName()
                            + ".");
        }
        if (expectedVersion != null && expectedVersion != claim.getVersion()) {
            throw new BusinessException(ErrorCode.STALE_VERSION,
                    "This claim was changed after you opened it. Reload the page and try again.");
        }
        if (reference == null || reference.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Enter the reimbursement reference.");
        }

        Instant now = Instant.now(clock);
        String finalReference = reference.trim();

        AuditEvent event = audit.record(AggregateType.CLAIM, auditKey(claim), AuditEventType.REIMBURSEMENT_RECORDED, admin,
                claim.getStatus().name(), ClaimStatus.REIMBURSED.name(), finalReference,
                snapshot(claim, claim.getDocumentsForCurrentRevision().size()));
        CourseApplication application = claim.getApplication();
        entitlements.find(application.getEmployee().getId(), application.getStartDate().getYear())
                .ifPresent(balance -> ledger.record(balance.account(), event, application, claim,
                        LedgerEntryType.REIMBURSE, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, claim.getAmount(), now));

        claim.markReimbursed(admin, finalReference, now);
        CourseClaim saved = claims.saveAndFlush(claim);

        outbox.enqueue(event, application.getEmployee(), OutboxTemplateCode.REIMBURSEMENT_RECORDED,
                Map.of("reference", application.getReferenceNo(),
                        "claimId", saved.getId(),
                        "applicationId", application.getId(),
                        "recipientName", application.getEmployee().getFullName(),
                        "summary", "Your fee claim of $" + ViewMapper.money(saved.getAmount()) + " was reimbursed under "
                                + finalReference + "."));
        return saved;
    }

    // ------------------------------------------------------------------ reads

    @Transactional(readOnly = true)
    public CourseClaim require(Long claimId) {
        return claims.findWithDetailsById(claimId).orElseThrow(() -> BusinessException.notFound("Claim"));
    }

    @Transactional(readOnly = true)
    public CourseClaim requireOwned(Long employeeId, Long claimId) {
        CourseClaim claim = claims.findWithDetailsById(claimId).orElseThrow(() -> BusinessException.notFound("Claim"));
        if (!claim.getApplication().getEmployee().getId().equals(employeeId)) {
            throw BusinessException.accessDenied("This claim belongs to another employee.");
        }
        return claim;
    }

    @Transactional(readOnly = true)
    public List<CourseClaim> forEmployee(Long employeeId) {
        return claims.findByEmployee(employeeId);
    }

    @Transactional(readOnly = true)
    public List<CourseClaim> pendingForManager(Long managerId) {
        return claims.findByApproverIdAndStatusInOrderBySubmittedAtAsc(managerId, List.of(ClaimStatus.SUBMITTED));
    }

    @Transactional(readOnly = true)
    public List<CourseClaim> approvedAwaitingReimbursement() {
        return claims.findByStatusOrderBySubmittedAtAsc(ClaimStatus.APPROVED);
    }

    @Transactional(readOnly = true)
    public long pendingCount(Long managerId) {
        return claims.countByApproverIdAndStatusIn(managerId, List.of(ClaimStatus.SUBMITTED));
    }

    /** Approved or completed reimbursable applications that have no claim yet. */
    @Transactional(readOnly = true)
    public List<CourseApplication> claimableApplications(Long employeeId) {
        return applications.findByEmployeeIdAndStatusInOrderByStartDateDesc(employeeId,
                List.of(ApplicationStatus.APPROVED, ApplicationStatus.COMPLETED)).stream()
                .filter(application -> application.getCategoryCode().isReimbursable())
                .filter(application -> claims.findByApplicationId(application.getId()).isEmpty())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ClaimDocument> documentsOf(Long claimId, int revision) {
        return documents.findByClaimIdAndClaimRevision(claimId, revision);
    }

    /** Loads a document only when the caller is the owner, the approver or an administrator. */
    @Transactional(readOnly = true)
    public ClaimDocument requireDocumentAccess(Long documentId, Employee actor, boolean isAdmin) {
        ClaimDocument document = documents.findById(documentId)
                .orElseThrow(() -> BusinessException.notFound("Document"));
        CourseClaim claim = claims.findWithDetailsById(document.getClaim().getId())
                .orElseThrow(() -> BusinessException.notFound("Claim"));
        boolean owner = claim.getApplication().getEmployee().getId().equals(actor.getId());
        boolean approver = claim.getApprover() != null && claim.getApprover().getId().equals(actor.getId());
        if (!owner && !approver && !isAdmin) {
            throw BusinessException.accessDenied("You cannot open this document.");
        }
        return document;
    }

    @Transactional(readOnly = true)
    public Optional<CourseClaim> findByApplication(Long applicationId) {
        return claims.findByApplicationId(applicationId);
    }

    // -------------------------------------------------------------- internals

    private void assertClaimable(CourseApplication application) {
        if (!(application.getStatus() == ApplicationStatus.APPROVED
                || application.getStatus() == ApplicationStatus.COMPLETED)) {
            throw new BusinessException(ErrorCode.CLAIM_NOT_ELIGIBLE,
                    "Only an approved or completed course can be claimed. This application is "
                            + application.getStatus().getDisplayName() + ".");
        }
        if (!application.getCategoryCode().isReimbursable()) {
            throw new BusinessException(ErrorCode.CLAIM_NOT_ELIGIBLE,
                    application.getCategoryCode().getDisplayName()
                            + " training is not reimbursable, so no claim can be raised.");
        }
    }

    private BigDecimal requireValidAmount(BigDecimal amount, CourseApplication application) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Enter a claimed amount above zero.");
        }
        BigDecimal fee = application.getCourseFee() == null ? BigDecimal.ZERO : application.getCourseFee();
        if (amount.compareTo(fee) > 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "The claimed amount cannot exceed the approved course fee of $" + ViewMapper.money(fee) + ".");
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private Employee resolveApprover(Employee employee, Long approverId) {
        if (approverId != null) {
            Employee chosen = requireEmployee(approverId);
            if (chosen.getId().equals(employee.getId())) {
                throw new BusinessException(ErrorCode.ROUTING_INVALID,
                        "You cannot approve your own claim. Choose a different approver.");
            }
            return chosen;
        }
        Employee manager = assignments.findWithManagerByEmployeeId(employee.getId())
                .map(assignment -> assignment.getManager())
                .orElseThrow(() -> new BusinessException(ErrorCode.NO_APPROVER,
                        "No approving manager is assigned to you yet. Ask an administrator to set your reporting line."));
        if (manager.getId().equals(employee.getId())) {
            throw new BusinessException(ErrorCode.ROUTING_INVALID,
                    "Your approving manager is yourself. Ask an administrator to assign a different manager.");
        }
        return manager;
    }

    private void requireDocuments(List<MultipartFile> receipts, String message) {
        boolean present = receipts != null && receipts.stream().anyMatch(file -> file != null && !file.isEmpty());
        if (!present) {
            throw new BusinessException(ErrorCode.DOCUMENT_MISSING, message);
        }
    }

    private void requireDocumentsOrExistingReceipt(CourseClaim claim, int newRevision, List<MultipartFile> receipts) {
        boolean uploads = receipts != null && receipts.stream().anyMatch(file -> file != null && !file.isEmpty());
        if (uploads) {
            return;
        }
        if (claim.getDocumentsForCurrentRevision().stream()
                .noneMatch(document -> document.getDocumentType() == DocumentType.RECEIPT)) {
            throw new BusinessException(ErrorCode.DOCUMENT_MISSING,
                    "Upload at least one receipt with this revision.");
        }
    }

    private List<String> storeDocuments(CourseClaim claim, List<MultipartFile> files, DocumentType documentType,
            Employee uploader, Instant now) {
        List<String> keys = new ArrayList<>();
        if (files == null) {
            return keys;
        }
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            DocumentStorageService.StoredDocument stored = storage.store(file, claim.getId());
            ClaimDocument document = new ClaimDocument();
            document.setClaim(claim);
            document.setClaimRevision(claim.getRevision());
            document.setDocumentType(documentType);
            document.setStorageKey(stored.storageKey());
            document.setOriginalName(stored.originalName());
            document.setDetectedContentType(stored.contentType());
            document.setSizeBytes(stored.sizeBytes());
            document.setSha256(stored.sha256());
            document.setUploadedBy(uploader);
            document.setUploadedAt(now);
            documents.save(document);
            keys.add(stored.storageKey());
        }
        return keys;
    }

    private void notifyApprover(AuditEvent event, CourseClaim claim, Employee approver) {
        outbox.enqueue(event, approver, OutboxTemplateCode.CLAIM_SUBMITTED,
                Map.of("reference", claim.getApplication().getReferenceNo(),
                        "claimId", claim.getId(),
                        "applicationId", claim.getApplication().getId(),
                        "recipientName", approver.getFullName(),
                        "summary", claim.getApplication().getEmployee().getFullName() + " submitted a fee claim of $"
                                + ViewMapper.money(claim.getAmount()) + " for "
                                + claim.getApplication().getCourseTitle() + "."));
    }

    private Employee requireEmployee(Long employeeId) {
        return employees.findById(employeeId).orElseThrow(() -> BusinessException.notFound("Employee"));
    }

    /** Audit timeline key of one claim revision. */
    public static String auditKey(CourseClaim claim) {
        return claim.getApplication().getReferenceNo() + "/CLM/" + claim.getRevision();
    }

    /** Every audit event of this claim, oldest revision first. */
    @Transactional(readOnly = true)
    public List<AuditEvent> timeline(CourseClaim claim) {
        List<AuditEvent> events = new ArrayList<>();
        for (int revision = 1; revision <= Math.max(claim.getRevision(), 1); revision++) {
            events.addAll(audit.timeline(AggregateType.CLAIM,
                    claim.getApplication().getReferenceNo() + "/CLM/" + revision));
        }
        return events;
    }

    private static String snapshot(CourseClaim claim, int documentCount) {
        return Json.write(Map.of(
                "reference", claim.getApplication().getReferenceNo(),
                "status", claim.getStatus().name(),
                "revision", claim.getRevision(),
                "amount", ViewMapper.money(claim.getAmount()),
                "documents", documentCount));
    }
}
