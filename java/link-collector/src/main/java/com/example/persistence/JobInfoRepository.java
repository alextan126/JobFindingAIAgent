package com.example.persistence;

import com.example.model.JobInfo;
import java.util.List;

/**
 * Repository interface for JobInfo persistence operations.
 */
public interface JobInfoRepository {

    /**
     * Save a JobInfo record to the database.
     * @param jobInfo the job information to save
     * @throws Exception if database operation fails
     */
    void save(JobInfo jobInfo) throws Exception;

    /**
     * Insert or update a JobInfo record (upsert operation).
     * If a record with the same job_link_id exists, it will be updated.
     * Otherwise, a new record will be inserted.
     * @param jobInfo the job information to save or update
     * @throws Exception if database operation fails
     */
    void upsert(JobInfo jobInfo) throws Exception;

    /**
     * Find job infos by job link IDs.
     * @param jobLinkIds list of job link IDs to search for
     * @return list of JobInfo records
     * @throws Exception if database operation fails
     */
    List<JobInfo> findByJobLinkIds(List<Integer> jobLinkIds) throws Exception;

    /**
     * Check if a job info record exists for a given job link ID.
     * @param jobLinkId the job link ID to check
     * @return true if exists, false otherwise
     * @throws Exception if database operation fails
     */
    boolean existsByJobLinkId(Integer jobLinkId) throws Exception;

    /**
     * Find all job info records in the database.
     * @return list of all JobInfo records
     * @throws Exception if database operation fails
     */
    List<JobInfo> findAll() throws Exception;
}
