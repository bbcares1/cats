package sg.edu.nus.cats.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sg.edu.nus.cats.domain.UserAccount;
import sg.edu.nus.cats.repository.UserAccountRepository;

/** Loads login accounts together with their permission roles. */
@Service
public class CatsUserDetailsService implements UserDetailsService {

    private final UserAccountRepository accounts;

    public CatsUserDetailsService(UserAccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserAccount account = accounts.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown username"));
        if (account.getRoleCodes().isEmpty()) {
            throw new UsernameNotFoundException("Account holds no role");
        }
        return new CatsUserPrincipal(account);
    }
}
