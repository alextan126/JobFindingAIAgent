package com.example.persistence;

import com.example.model.JobLink;
import java.util.List;

public interface JobLinkRepository {
    void saveAllIgnoreDuplicates(List<JobLink> links) throws Exception;
}
