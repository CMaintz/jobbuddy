package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.domain.job.JobCategory;
import com.autoapplicant.domain.job.JobCategoryClassifier;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Re-runs the category rules on active jobs once per rules version.
 *
 * <p>Nothing else revisits a stored category: ingestion only refreshes a known posting's last-seen
 * time, and enrichment only fills an empty one. After a rule change, jobs filed under the old rules
 * keep their category until they expire, and the hard industry filter keeps hiding them.
 *
 * <p>The version is claimed by inserting it in the same transaction as the updates, so two instances
 * starting together run it once, and a failed run leaves the version unclaimed for the next start.
 */
@Component
public class JobCategoryBackfill {

    private static final Logger log = LoggerFactory.getLogger(JobCategoryBackfill.class);

    /** Bump when a classifier change should reach jobs that are already stored. */
    static final int RULES_VERSION = 1;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final JobCategoryClassifier classifier = new JobCategoryClassifier();

    public JobCategoryBackfill(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /**
     * Never fails startup. The crawler CLI runs with Flyway off and can meet a database that has
     * not reached V042; an exception here would turn a finished crawl into a failed run. The
     * transaction rolls back, so the version stays unclaimed and the next start tries again.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        try {
            int moved = run();
            if (moved >= 0) log.info("Job category rules v{} applied: {} active jobs moved", RULES_VERSION, moved);
        } catch (DataAccessException e) {
            log.warn("Job category rules v{} not applied, will retry on next start: {}",
                    RULES_VERSION, e.getMessage());
        }
    }

    /** @return the number of jobs whose category changed, or -1 when this version already ran */
    public int run() {
        Integer moved = tx.execute(status -> {
            int claimed = jdbc.update("INSERT INTO job_category_backfill (rules_version) VALUES (?)"
                    + " ON CONFLICT DO NOTHING", RULES_VERSION);
            if (claimed == 0) return -1;
            List<Object[]> changes = new ArrayList<>();
            jdbc.query("SELECT id, job_category, title, left(description_clean, 300) AS preview"
                    + " FROM jobs WHERE is_active", rs -> {
                        String raw = rs.getString("job_category");
                        JobCategory stored = parse(raw);
                        if (raw != null && stored == null) return;
                        JobCategory now = classifier.reclassify(stored, rs.getString("title"),
                                rs.getString("preview"));
                        if (now != stored) changes.add(new Object[] {now.name(), rs.getObject("id")});
                    });
            jdbc.batchUpdate("UPDATE jobs SET job_category = ? WHERE id = ?", changes);
            return changes.size();
        });
        return moved != null ? moved : -1;
    }

    /** Null for a value this build does not know; the caller leaves such a job alone. */
    private static JobCategory parse(String stored) {
        if (stored == null) return null;
        try {
            return JobCategory.valueOf(stored);
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }
}
