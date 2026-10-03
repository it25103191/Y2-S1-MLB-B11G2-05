package com.safari.tms.repo;

import com.safari.tms.domain.User;
import com.safari.tms.domain.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    List<User> findByRoleOrderByFullNameAsc(Role role);
    List<User> findByRoleInOrderByFullNameAsc(List<Role> roles);
    long countByRole(Role role);
}
