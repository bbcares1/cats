package sg.edu.nus.cats.web;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import sg.edu.nus.cats.support.BusinessException;
import sg.edu.nus.cats.support.ErrorCode;

/**
 * Turns expected rule violations into a readable page (or JSON for the API),
 * so no screen ever shows a stack trace to the user.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public Object business(BusinessException ex, HttpServletRequest request, Model model) {
        HttpStatus status = HttpStatus.resolve(ex.getCode().getHttpStatus());
        HttpStatus resolved = status == null ? HttpStatus.BAD_REQUEST : status;
        if (wantsJson(request)) {
            return json(resolved, ex.getCode(), ex.getMessage(), Map.of());
        }
        model.addAttribute("status", resolved.value());
        model.addAttribute("code", ex.getCode().name());
        model.addAttribute("message", ex.getMessage());
        model.addAttribute("path", request.getRequestURI());
        model.addAttribute("title", titleFor(ex.getCode()));
        return new ModelAndView("error/business", model.asMap(), resolved);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public Object denied(AccessDeniedException ex, HttpServletRequest request, Model model) {
        if (wantsJson(request)) {
            return json(HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED,
                    "You are not allowed to perform this action.", Map.of());
        }
        model.addAttribute("status", 403);
        model.addAttribute("code", ErrorCode.ACCESS_DENIED.name());
        model.addAttribute("message", "You do not have permission to open this page or perform this action.");
        model.addAttribute("path", request.getRequestURI());
        model.addAttribute("title", "Not allowed");
        return new ModelAndView("error/business", model.asMap(), HttpStatus.FORBIDDEN);
    }

    /** Optimistic-lock failure: the record changed while this form was open. */
    @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    public Object stale(Object ex, HttpServletRequest request, Model model) {
        String message = "This record was changed by someone else while you were working on it. "
                + "Reload the page to see the latest version and try again.";
        if (wantsJson(request)) {
            return json(HttpStatus.CONFLICT, ErrorCode.STALE_VERSION, message, Map.of());
        }
        model.addAttribute("status", 409);
        model.addAttribute("code", ErrorCode.STALE_VERSION.name());
        model.addAttribute("message", message);
        model.addAttribute("path", request.getRequestURI());
        model.addAttribute("title", "Record changed");
        return new ModelAndView("error/business", model.asMap(), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Object tooLarge(MaxUploadSizeExceededException ex, HttpServletRequest request, Model model) {
        String message = "The attachment is larger than the 5 MB limit. Compress the file or upload fewer pages.";
        if (wantsJson(request)) {
            return json(HttpStatus.BAD_REQUEST, ErrorCode.FILE_TOO_LARGE, message, Map.of());
        }
        model.addAttribute("status", 400);
        model.addAttribute("code", ErrorCode.FILE_TOO_LARGE.name());
        model.addAttribute("message", message);
        model.addAttribute("path", request.getRequestURI());
        model.addAttribute("title", "Attachment too large");
        return new ModelAndView("error/business", model.asMap(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Object illegal(IllegalArgumentException ex, HttpServletRequest request, Model model) {
        log.debug("Bad request on {}: {}", request.getRequestURI(), ex.getMessage());
        if (wantsJson(request)) {
            return json(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST,
                    "The request contained an invalid value.", Map.of("detail", String.valueOf(ex.getMessage())));
        }
        model.addAttribute("status", 400);
        model.addAttribute("code", ErrorCode.INVALID_REQUEST.name());
        model.addAttribute("message", "The request contained an invalid value: " + ex.getMessage());
        model.addAttribute("path", request.getRequestURI());
        model.addAttribute("title", "Invalid request");
        return new ModelAndView("error/business", model.asMap(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public Object unexpected(Exception ex, HttpServletRequest request, Model model) {
        log.error("Unhandled error on {}", request.getRequestURI(), ex);
        if (wantsJson(request)) {
            return json(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INVALID_REQUEST,
                    "Something went wrong. The action was not applied.", Map.of());
        }
        model.addAttribute("status", 500);
        model.addAttribute("code", "UNEXPECTED");
        model.addAttribute("message", "Something went wrong and the action was not applied. "
                + "No training day, dollar or status was changed. Try again, and contact the administrator if it "
                + "keeps happening.");
        model.addAttribute("path", request.getRequestURI());
        model.addAttribute("title", "Unexpected problem");
        return new ModelAndView("error/business", model.asMap(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private boolean wantsJson(HttpServletRequest request) {
        String path = request.getRequestURI();
        String accept = request.getHeader("Accept");
        return path.startsWith("/api/") || (accept != null && accept.contains(MediaType.APPLICATION_JSON_VALUE));
    }

    private ResponseEntity<Map<String, Object>> json(HttpStatus status, ErrorCode code, String message,
            Map<String, Object> extras) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("code", code.name());
        body.put("message", message);
        List<String> fields = new ArrayList<>();
        body.put("fieldErrors", fields);
        body.putAll(extras);
        return ResponseEntity.status(status).body(body);
    }

    private String titleFor(ErrorCode code) {
        return switch (code) {
            case ACCESS_DENIED -> "Not allowed";
            case NOT_FOUND -> "Not found";
            case STALE_VERSION -> "Record changed";
            case DUPLICATE_REQUEST -> "Already submitted";
            case NO_APPROVER -> "No approving manager";
            case ACCOUNT_NOT_CONFIGURED, ENTITLEMENT_BELOW_USAGE -> "Training entitlement issue";
            case INSUFFICIENT_UNITS, INSUFFICIENT_BUDGET -> "Not enough entitlement left";
            case OVERLAPPING_APPLICATION -> "Overlapping training";
            case NO_WORKING_DAY -> "No training day selected";
            case INVALID_STATE -> "This action is not available";
            default -> "Request could not be completed";
        };
    }
}
