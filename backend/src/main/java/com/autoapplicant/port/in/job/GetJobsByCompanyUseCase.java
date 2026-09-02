package com.autoapplicant.port.in.job;

import com.autoapplicant.domain.job.Job;

import java.util.List;
import java.util.UUID;

public interface GetJobsByCompanyUseCase {
    /** Every posting we hold from this company, live ones first. */
    List<Job> getJobsByCompany(UUID companyId);
}
