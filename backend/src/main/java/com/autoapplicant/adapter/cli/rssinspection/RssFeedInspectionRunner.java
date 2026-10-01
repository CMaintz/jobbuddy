package com.autoapplicant.adapter.cli.rssinspection;

import com.autoapplicant.config.AppProperties;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Manual diagnostic that prints what the crawled RSS feeds actually contain, to check
 * a connector's assumptions against the live sources. Not part of the test suite.
 *
 * <ol>
 *   <li>Teamtailor: the first item's fields from a few companies, flagging fields not
 *       every company exposes.
 *   <li>Teamtailor: job counts per configured career page, flagging dead URLs.
 *   <li>Jobindex and IT-Jobbank: the fields of one item and its detail page's structure.
 * </ol>
 *
 * <p>Run from the repo root: {@code SPRING_PROFILES_ACTIVE=rss-test ./gradlew :backend:bootRun}
 */
@Component
@Profile("rss-test")
public class RssFeedInspectionRunner implements ApplicationRunner {

    private static final String JOBINDEX_RSS = "https://www.jobindex.dk/jobsoegning.rss?page=1";
    private static final String IT_JOBBANK_RSS = "https://www.it-jobbank.dk/jobsoegning.rss?page=1";

    private final AppProperties appProperties;

    public RssFeedInspectionRunner(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        RssFeedClient feeds = new RssFeedClient();
        List<String> teamtailorUrls = appProperties.getTeamtailor().getCareerPageUrls();

        new TeamtailorFieldSurvey(feeds).print(teamtailorUrls);
        new TeamtailorUrlCheck(feeds).print(teamtailorUrls);
        FeedStructureInspector inspector = new FeedStructureInspector(feeds);
        inspector.print("Jobindex", JOBINDEX_RSS);
        inspector.print("IT-Jobbank", IT_JOBBANK_RSS);
    }
}
