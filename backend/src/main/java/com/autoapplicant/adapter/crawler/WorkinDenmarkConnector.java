package com.autoapplicant.adapter.crawler;

import com.autoapplicant.domain.job.JobSource;
import org.springframework.stereotype.Component;

@Component
public class WorkinDenmarkConnector extends AbstractJobSourceConnector {

    @Override
    public JobSource getSource() {
        return JobSource.WORKIN_DENMARK;
    }
}
