package com.autoapplicant.config;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.ValidateResult;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Stops the crawler profile at startup when the database is not migrated to what this build
 * expects.
 *
 * <p>The crawl shares its database with the desktop app, which is the one migrator, so the crawl
 * runs with Flyway off. Without this check a crawl built from newer code than the schema would
 * start and then fail on a missing table or column partway through writing postings.
 */
@Component
@Profile("crawler")
public class SchemaVersionCheck implements InitializingBean {

    private final DataSource dataSource;
    private final String[] locations;

    public SchemaVersionCheck(DataSource dataSource,
                              @Value("${spring.flyway.locations:classpath:db/migration}") String locations) {
        this.dataSource = dataSource;
        this.locations = locations.split(",");
    }

    @Override
    public void afterPropertiesSet() {
        verify();
    }

    /** @throws IllegalStateException when a migration this build ships is missing from the database */
    public void verify() {
        ValidateResult result = Flyway.configure()
                .dataSource(dataSource)
                .locations(locations)
                .load()
                .validateWithResult();
        if (!result.validationSuccessful) {
            throw new IllegalStateException("The database schema is not migrated to what this build"
                    + " expects. Start the app that migrates this database first, or build the"
                    + " crawler from the same release. Flyway: " + result.getAllErrorMessages());
        }
    }
}
