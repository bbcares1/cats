package sg.edu.nus.cats.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import sg.edu.nus.cats.security.CatsUserPrincipal;

/**
 * Sign-in entry points and the post-login landing rule.
 * P01: one visual template, two distinct entry points — no shared "any role" door.
 */
@Controller
public class AuthController {

    @GetMapping("/login")
    public String staffLogin() {
        return "auth/login";
    }

    @GetMapping("/admin/login")
    public String adminLogin() {
        return "auth/admin-login";
    }

    @GetMapping("/")
    public String root() {
        return "redirect:/home";
    }

    @GetMapping("/home")
    public String home(@AuthenticationPrincipal CatsUserPrincipal principal, Model model) {
        if (principal == null) {
            return "redirect:/login";
        }
        model.addAttribute("fullName", principal.getFullName());
        if (principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            return "redirect:/admin";
        }
        if (principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_MANAGER"))) {
            return "redirect:/manager/approvals";
        }
        return "redirect:/employee/dashboard";
    }
}
