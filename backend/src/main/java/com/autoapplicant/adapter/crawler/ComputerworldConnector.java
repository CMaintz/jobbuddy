package com.autoapplicant.adapter.crawler;

import com.autoapplicant.domain.job.JobSource;
import org.springframework.stereotype.Component;

@Component
public class ComputerworldConnector extends AbstractJobSourceConnector {

    @Override
    public JobSource getSource() {
        return JobSource.COMPUTERWORLD;
    }
}
