package com.autoapplicant.usecase.job;

import com.autoapplicant.domain.job.Job;
import com.autoapplicant.port.in.job.GetJobsByCompanyUseCase;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class GetJobsByCompanyService implements GetJobsByCompanyUseCase {

    /** A company page is a browse, not an export — enough to see the shape of their hiring. */
    private static final int MAX_JOBS = 100;

    private final JobRepositoryPort jobRepo;

    public GetJobsByCompanyService(JobRepositoryPort jobRepo) {
        this.jobRepo = jobRepo;
    }

    @Override
    public List<Job> getJobsByCompany(UUID companyId) {
        return jobRepo.findByCompanyId(companyId, MAX_JOBS);
    }
}
