package com.example.persistence;

import com.example.model.User;
import java.util.Optional;

/**
 * Repository interface for User persistence operations.
 */
public interface UserRepository {

    /**
     * Create a new user account.
     * @param user the user to create
     * @throws Exception if user creation fails or email already exists
     */
    void create(User user) throws Exception;

    /**
     * Find a user by email.
     * @param email the user's email
     * @return Optional containing the user if found
     * @throws Exception if database operation fails
     */
    Optional<User> findByEmail(String email) throws Exception;

    /**
     * Find a user by ID.
     * @param id the user's ID
     * @return Optional containing the user if found
     * @throws Exception if database operation fails
     */
    Optional<User> findById(Integer id) throws Exception;

    /**
     * Update user information.
     * @param user the user with updated information
     * @throws Exception if update fails
     */
    void update(User user) throws Exception;

    /**
     * Check if a user exists with the given email.
     * @param email the email to check
     * @return true if user exists
     * @throws Exception if database operation fails
     */
    boolean existsByEmail(String email) throws Exception;
}
