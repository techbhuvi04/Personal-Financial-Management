package com.example.finance.repository;

import com.example.finance.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence operations for {@link User}.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * @param username canonical (lower-case) email address
     * @return the matching user, if any
     */
    Optional<User> findByUsername(String username);

    /**
     * @param username canonical (lower-case) email address
     * @return whether an account already uses this username
     */
    boolean existsByUsername(String username);
}
