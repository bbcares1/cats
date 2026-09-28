package sg.edu.nus.cats.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import sg.edu.nus.cats.domain.UserAccount;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    @EntityGraph(attributePaths = { "roles", "employee" })
    Optional<UserAccount> findByUsernameIgnoreCase(String username);

    @EntityGraph(attributePaths = { "roles", "employee" })
    Optional<UserAccount> findByEmployeeId(Long employeeId);

    boolean existsByUsernameIgnoreCase(String username);

    long countByEnabledTrue();
}
