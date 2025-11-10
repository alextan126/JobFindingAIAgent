package com.example.persistence;

import com.example.model.Application;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Application persistence operations.
 */
public interface ApplicationRepository {

    /**
     * Create a new job application.
     * @param application the application to create
     * @throws Exception if application creation fails or duplicate exists
     */
    void create(Application application) throws Exception;

    /**
     * Find an application by ID.
     * @param id the application ID
     * @return Optional containing the application if found
     * @throws Exception if database operation fails
     */
    Optional<Application> findById(Integer id) throws Exception;

    /**
     * Find all applications for a user.
     * @param userId the user's ID
     * @return list of applications
     * @throws Exception if database operation fails
     */
    List<Application> findByUserId(Integer userId) throws Exception;

    /**
     * Find applications for a user with a specific status.
     * @param userId the user's ID
     * @param status the application status
     * @return list of applications
     * @throws Exception if database operation fails
     */
    List<Application> findByUserIdAndStatus(Integer userId, String status) throws Exception;

    /**
     * Update application status and notes.
     * @param application the application with updated information
     * @throws Exception if update fails
     */
    void update(Application application) throws Exception;

    /**
     * Check if user has already applied to a job.
     * @param userId the user's ID
     * @param jobInfoId the job info ID
     * @return true if application exists
     * @throws Exception if database operation fails
     */
    boolean existsByUserAndJob(Integer userId, Integer jobInfoId) throws Exception;

    /**
     * Count applications by status for a user.
     * @param userId the user's ID
     * @return list of status counts
     * @throws Exception if database operation fails
     */
    List<StatusCount> countByStatus(Integer userId) throws Exception;

    /**
     * Record for status count results.
     */
    record StatusCount(String status, int count) {}
}
