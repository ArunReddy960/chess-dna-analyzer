package com.chessdna.chessdnaanalyzer;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

@EnabledIfEnvironmentVariable(named = "CI_DATABASE_URL", matches = ".+")
class FlywayMigrationTest {

    @Test
    void migration_upgradesExistingJobsWithoutLosingRows() throws Exception {
        String url = System.getenv("CI_DATABASE_URL");
        String username = System.getenv("CI_DATABASE_USERNAME");
        String password = System.getenv("CI_DATABASE_PASSWORD");

        try (Connection connection = DriverManager.getConnection(url, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS flyway_schema_history");
            statement.execute("DROP TABLE IF EXISTS analysis_jobs");
            statement.execute("""
                    CREATE TABLE analysis_jobs (
                        id BIGSERIAL PRIMARY KEY,
                        username VARCHAR(255),
                        game_count INTEGER NOT NULL,
                        status VARCHAR(255)
                    )
                    """);
            statement.execute("""
                    INSERT INTO analysis_jobs (username, game_count, status)
                    VALUES ('legacy-player', 8, 'COMPLETED')
                    """);
        }

        Flyway.configure()
                .dataSource(url, username, password)
                .baselineOnMigrate(true)
                .baselineVersion(MigrationVersion.fromVersion("0"))
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(url, username, password);
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("""
                     SELECT platform, stage, depth, completed_games, cache_hits
                     FROM analysis_jobs
                     WHERE username = 'legacy-player'
                     """)) {
            result.next();
            assertEquals("lichess", result.getString("platform"));
            assertEquals("COMPLETED", result.getString("stage"));
            assertEquals(12, result.getInt("depth"));
            assertEquals(8, result.getInt("completed_games"));
            assertEquals(0, result.getInt("cache_hits"));
        }
    }
}
