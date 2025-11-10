package com.example.persistence;

import com.example.model.JobLink;
import com.example.model.JobLinkWithId;
import java.util.List;

public interface JobLinkRepository {
    void saveAllIgnoreDuplicates(List<JobLink> links) throws Exception;

    /**
     * Find job links with status='new' (not yet scraped).
     * @param limit maximum number of links to return
     * @return list of unscraped job links with their IDs
     * @throws Exception if database operation fails
     */
    List<JobLinkWithId> findUnscrapedLinks(int limit) throws Exception;

    /**
     * Update a job link's status to 'scraped' and set the scraped_at timestamp.
     * @param jobLinkId the ID of the job link to update
     * @throws Exception if database operation fails
     */
    void markAsScraped(Integer jobLinkId) throws Exception;

    /**
     * Update a job link's status to 'error' and record the error message.
     * @param jobLinkId the ID of the job link to update
     * @param errorMessage the error message to record
     * @throws Exception if database operation fails
     */
    void markAsError(Integer jobLinkId, String errorMessage) throws Exception;
}
