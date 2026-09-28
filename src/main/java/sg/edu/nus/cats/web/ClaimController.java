package sg.edu.nus.cats.web;

import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import sg.edu.nus.cats.domain.ClaimDocument;
import sg.edu.nus.cats.domain.CourseClaim;
import sg.edu.nus.cats.domain.Employee;
import sg.edu.nus.cats.dto.ClaimForm;
import sg.edu.nus.cats.domain.enums.ClaimStatus;
import sg.edu.nus.cats.dto.ReimburseForm;
import sg.edu.nus.cats.repository.CourseClaimRepository;
import sg.edu.nus.cats.security.CurrentUser;
import sg.edu.nus.cats.service.AuditService;
import sg.edu.nus.cats.service.ClaimService;
import sg.edu.nus.cats.service.DocumentStorageService;
import sg.edu.nus.cats.support.BusinessException;

/**
 * P06 claims (employee side), the manager fee claim tab and P15 reimbursement registration.
 * Money is only ever moved through these semantic endpoints — never by editing a status field.
 */
@Controller
public class ClaimController {

    private final CurrentUser currentUser;
    private final ClaimService claims;
    private final AuditService audit;
    private final DocumentStorageService storage;
    private final CourseClaimRepository claimRepository;

    public ClaimController(CurrentUser currentUser, ClaimService claims, AuditService audit,
            DocumentStorageService storage, CourseClaimRepository claimRepository) {
        this.currentUser = currentUser;
        this.claims = claims;
        this.audit = audit;
        this.storage = storage;
        this.claimRepository = claimRepository;
    }

    /* ------------------------------------------------------------- P06 list */

    @GetMapping("/employee/claims")
    @Transactional(readOnly = true)
    public String myClaims(Model model) {
        Employee employee = currentUser.require();
        model.addAttribute("claims", claims.forEmployee(employee.getId()));
        model.addAttribute("claimable", claims.claimableApplications(employee.getId()));
        return "employee/claims";
    }

    @GetMapping("/employee/claims/new")
    @Transactional(readOnly = true)
    public String newClaim(@RequestParam Long applicationId, Model model) {
        Employee employee = currentUser.require();
        List<sg.edu.nus.cats.domain.CourseApplication> claimable = claims.claimableApplications(employee.getId());
        sg.edu.nus.cats.domain.CourseApplication application = claimable.stream()
                .filter(candidate -> candidate.getId().equals(applicationId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(sg.edu.nus.cats.support.ErrorCode.CLAIM_NOT_ELIGIBLE,
                        "This course cannot be claimed. Only an approved course that has ended and has no existing "
                                + "claim can be reimbursed."));
        ClaimForm form = new ClaimForm();
        form.setApplicationId(application.getId());
        form.setAmount(application.getCourseFee());
        form.setApproverId(application.getApprover() == null ? null : application.getApprover().getId());
        form.setClientRequestId(java.util.UUID.randomUUID().toString());
        model.addAttribute("form", form);
        model.addAttribute("application", application);
        model.addAttribute("claim", null);
        return "employee/claim-form";
    }

    @PostMapping("/employee/claims")
    public String submit(@ModelAttribute("form") ClaimForm form,
            @RequestParam(required = false) List<MultipartFile> receipts,
            @RequestParam(required = false) List<MultipartFile> certificates, RedirectAttributes flash) {
        Employee employee = currentUser.require();
        CourseClaim claim = claims.submit(employee.getId(), form, receipts, certificates);
        flash.addFlashAttribute("message", "Claim submitted to " + claim.getApprover().getFullName()
                + " with " + claim.getDocumentsForCurrentRevision().size() + " attachment(s).");
        return "redirect:/employee/claims/" + claim.getId();
    }

    @GetMapping("/employee/claims/{id}")
    @Transactional(readOnly = true)
    public String claimDetail(@PathVariable Long id, Model model) {
        Employee employee = currentUser.require();
        CourseClaim claim = claims.requireOwned(employee.getId(), id);
        model.addAttribute("claim", claim);
        model.addAttribute("documents", claims.documentsOf(claim.getId(), claim.getRevision()));
        model.addAttribute("timeline", claims.timeline(claim));
        return "employee/claim-detail";
    }

    @GetMapping("/employee/claims/{id}/revise")
    @Transactional(readOnly = true)
    public String reviseForm(@PathVariable Long id, Model model) {
        Employee employee = currentUser.require();
        CourseClaim claim = claims.requireOwned(employee.getId(), id);
        if (!claim.getStatus().isRevisable()) {
            throw new BusinessException(sg.edu.nus.cats.support.ErrorCode.INVALID_STATE,
                    "Only a rejected claim can be revised and resubmitted.");
        }
        ClaimForm form = new ClaimForm();
        form.setApplicationId(claim.getApplication().getId());
        form.setAmount(claim.getAmount());
        form.setPaidByEmployee(claim.isPaidByEmployee());
        form.setApproverId(claim.getApprover() == null ? null : claim.getApprover().getId());
        form.setVersion(claim.getVersion());
        form.setClientRequestId(java.util.UUID.randomUUID().toString());
        model.addAttribute("form", form);
        model.addAttribute("application", claim.getApplication());
        model.addAttribute("claim", claim);
        return "employee/claim-form";
    }

    @PostMapping("/employee/claims/{id}/resubmit")
    public String resubmit(@PathVariable Long id, @ModelAttribute("form") ClaimForm form,
            @RequestParam(required = false) List<MultipartFile> receipts,
            @RequestParam(required = false) List<MultipartFile> certificates, RedirectAttributes flash) {
        Employee employee = currentUser.require();
        CourseClaim claim = claims.revise(employee.getId(), id, form, receipts, certificates);
        flash.addFlashAttribute("message", "Claim revised and resubmitted as revision " + claim.getRevision() + ".");
        return "redirect:/employee/claims/" + id;
    }

    /* --------------------------------------------------- manager decision */

    @PostMapping("/manager/claims/{id}/decision")
    public String decide(@PathVariable Long id, @RequestParam boolean approved, @RequestParam String reason,
            @RequestParam(required = false) Long version, RedirectAttributes flash) {
        Employee manager = currentUser.require();
        claims.decide(manager.getId(), id, approved, reason, version);
        flash.addFlashAttribute("message", "Claim " + (approved ? "approved" : "rejected") + ".");
        return "redirect:/manager/approvals?tab=claims";
    }

    /* ------------------------------------------------ P15 admin reimbursement */

    @GetMapping("/admin/reimbursements")
    @Transactional(readOnly = true)
    public String reimbursementQueue(@RequestParam(defaultValue = "pending") String tab, Model model) {
        List<CourseClaim> pending = claims.approvedAwaitingReimbursement();
        model.addAttribute("pending", pending);
        model.addAttribute("pendingTotal", pending.stream()
                .map(CourseClaim::getAmount)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add));
        model.addAttribute("history", claimRepository.findByStatusOrderBySubmittedAtAsc(ClaimStatus.REIMBURSED));
        model.addAttribute("tab", "history".equals(tab) ? "history" : "pending");
        model.addAttribute("reimburseForm", new ReimburseForm());
        return "admin/reimbursements";
    }

    @PostMapping("/admin/claims/{id}/reimburse")
    public String reimburse(@PathVariable Long id, @ModelAttribute("reimburseForm") ReimburseForm form,
            RedirectAttributes flash) {
        Employee admin = currentUser.require();
        CourseClaim claim = claims.reimburse(admin.getId(), id, form.getReference(), form.getVersion());
        flash.addFlashAttribute("message", "Reimbursement " + claim.getReimbursementReference()
                + " recorded in demo; no payment is initiated.");
        return "redirect:/admin/reimbursements";
    }

    /* ------------------------------------------------------------ downloads */

    @GetMapping("/claims/documents/{documentId}")
    @Transactional(readOnly = true)
    public ResponseEntity<Resource> download(@PathVariable Long documentId) {
        Employee actor = currentUser.require();
        boolean isAdmin = currentUser.principal().getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
        ClaimDocument document = claims.requireDocumentAccess(documentId, actor, isAdmin);
        Resource resource = storage.load(document.getStorageKey());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + document.getOriginalName() + "\"")
                .contentType(MediaType.parseMediaType(document.getDetectedContentType()))
                .contentLength(document.getSizeBytes())
                .body(resource);
    }

}
