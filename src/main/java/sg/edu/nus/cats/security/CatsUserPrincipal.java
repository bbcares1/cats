package sg.edu.nus.cats.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import sg.edu.nus.cats.domain.UserAccount;
import sg.edu.nus.cats.domain.enums.RoleCode;

/**
 * Authenticated principal. Carries the staff identity so controllers can resolve
 * the acting employee without a second database lookup, and one authority per role.
 */
public class CatsUserPrincipal implements UserDetails {

    private static final long serialVersionUID = 1L;

    private final Long accountId;
    private final Long employeeId;
    private final String username;
    private final String passwordHash;
    private final String fullName;
    private final boolean enabled;
    private final List<GrantedAuthority> authorities;

    public CatsUserPrincipal(UserAccount account) {
        this.accountId = account.getId();
        this.employeeId = account.getEmployee().getId();
        this.username = account.getUsername();
        this.passwordHash = account.getPasswordHash();
        this.fullName = account.getEmployee().getFullName();
        this.enabled = account.isEnabled();
        this.authorities = account.getRoleCodes().stream()
                .map(RoleCode::authority)
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
    }

    public Long getAccountId() {
        return accountId;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public String getFullName() {
        return fullName;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
