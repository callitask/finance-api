package com.treishvaam.financeapi.repository;

import com.treishvaam.financeapi.model.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

  /**
   * Finds a user by their username. This is used for authentication and security checks.
   *
   * @param username The username to search for.
   * @return An Optional containing the User if found, or empty if not.
   */
  Optional<User> findByUsername(String username);

  /**
   * Finds a user by their email address. This is used for the login process.
   *
   * @param email The email to search for.
   * @return An Optional containing the User if found, or empty if not.
   */
  Optional<User> findByEmail(String email);
}
