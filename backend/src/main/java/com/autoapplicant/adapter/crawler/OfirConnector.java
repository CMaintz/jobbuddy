package com.autoapplicant.adapter.crawler;

import com.autoapplicant.domain.job.JobSource;

// ofir.dk redirects to jobindex.dk — same underlying data already covered by JobindexConnector.
// Keeping the class so the OFIR enum value stays mapped, but not registering it as a Spring bean.
public class OfirConnector extends AbstractJobSourceConnector {

    @Override
    public JobSource getSource() {
        return JobSource.OFIR;
    }
}
