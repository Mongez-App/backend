package com.smartstudy.planning.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Flyway configuration that performs a repair before migrate.
 * This handles checksum mismatches caused by modified migration scripts
 * and cleans up failed migration entries from the schema history table.
 */
@Configuration
public class FlywayRepairConfig {

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return (flyway) -> {
            flyway.repair();
            flyway.migrate();
        };
    }
}
