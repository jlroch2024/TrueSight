package com.truesight.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Reads and saves users. Spring writes the code for these methods from their names: {@code findByEmail} becomes
 * "SELECT ... FROM users WHERE email = ?".
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
