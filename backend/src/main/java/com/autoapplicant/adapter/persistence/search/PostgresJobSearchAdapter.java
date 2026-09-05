package com.autoapplicant.adapter.persistence.search;

import com.autoapplicant.adapter.persistence.entity.JobEntity;
import com.autoapplicant.adapter.persistence.mapper.JobMapper;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.search.JobSearchFilters;
import com.autoapplicant.domain.search.JobSearchQuery;
import com.autoapplicant.domain.search.JobSearchResult;
import com.autoapplicant.port.out.job.JobSearchPort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Keyword search straight out of Postgres, over the generated {@code search_vector}
 * column. Ranked by ts_rank_cd so a title hit outweighs a mention buried in the body,
 * with the newest posting winning ties.
 *
 * <p>An empty query is a browse rather than a search, and falls back to newest-first.
 */
@Component
public class PostgresJobSearchAdapter implements JobSearchPort {

    /** The stemmer the generated column was built with; the query must match it. */
    private static final String TEXT_CONFIG = "danish";

    @PersistenceContext
    private EntityManager em;

    @Override
    @Transactional(readOnly = true)
    public JobSearchResult search(JobSearchQuery query) {
        String text = query.text() == null ? "" : query.text().trim();
        boolean hasText = !text.isBlank();

        StringBuilder where = new StringBuilder(" WHERE j.is_active = true");
        List<Object> params = new ArrayList<>();

        if (hasText) {
            where.append(" AND j.search_vector @@ websearch_to_tsquery(:cfg, :q)");
        }
        appendCategoryFilter(query.filters(), where);

        String order = hasText
                ? " ORDER BY ts_rank_cd(j.search_vector, websearch_to_tsquery(:cfg, :q)) DESC, j.posted_at DESC NULLS LAST"
                : " ORDER BY j.posted_at DESC NULLS LAST";

        int page = Math.max(0, query.page());
        int size = Math.min(Math.max(query.size(), 1), 100);

        Query rows = em.createNativeQuery("SELECT j.* FROM jobs j" + where + order, JobEntity.class);
        Query count = em.createNativeQuery("SELECT count(*) FROM jobs j" + where);
        bind(rows, query, hasText, text);
        bind(count, query, hasText, text);

        rows.setFirstResult(page * size);
        rows.setMaxResults(size);

        @SuppressWarnings("unchecked")
        List<Job> jobs = ((List<JobEntity>) rows.getResultList()).stream().map(JobMapper::toDomain).toList();
        long total = ((Number) count.getSingleResult()).longValue();

        // Facets were computed and discarded by every caller; the filter lists the feed
        // shows are derived from the rows it already has.
        return new JobSearchResult(jobs, total, page, size, Map.of());
    }

    private static void appendCategoryFilter(JobSearchFilters filters, StringBuilder where) {
        if (filters != null && filters.jobCategories() != null && !filters.jobCategories().isEmpty()) {
            where.append(" AND j.job_category IN (:categories)");
        }
    }

    private static void bind(Query q, JobSearchQuery query, boolean hasText, String text) {
        if (hasText) {
            q.setParameter("cfg", TEXT_CONFIG);
            q.setParameter("q", text);
        }
        JobSearchFilters filters = query.filters();
        if (filters != null && filters.jobCategories() != null && !filters.jobCategories().isEmpty()) {
            q.setParameter("categories", filters.jobCategories());
        }
    }
}
