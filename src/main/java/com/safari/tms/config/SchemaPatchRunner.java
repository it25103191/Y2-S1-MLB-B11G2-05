package com.safari.tms.config;

import com.safari.tms.domain.enums.PaymentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Keeps enum CHECK constraints in step with the Java enums.
 *
 * <p>Hibernate generates a CHECK constraint for every {@code @Enumerated(STRING)} column, but
 * {@code ddl-auto: update} never alters an existing constraint. Adding a value to an enum (for
 * example {@link PaymentStatus#VOIDED}) would therefore make inserts fail on any database created
 * by an older build. This runs before the seeder and rebuilds a constraint only when it is missing
 * a value, so it is a no-op on fresh databases.
 */
@Component
@Order(0)
public class SchemaPatchRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaPatchRunner.class);

    private final JdbcTemplate jdbc;
    private final DataSource dataSource;

    public SchemaPatchRunner(JdbcTemplate jdbc, DataSource dataSource) {
        this.jdbc = jdbc;
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!isSqlServer()) {
            return;
        }
        ensureEnumCheck("payments", "status", PaymentStatus.class);
    }

    /** Rebuilds the CHECK constraint on {@code table.column} if it lacks any value of {@code type}. */
    private void ensureEnumCheck(String table, String column, Class<? extends Enum<?>> type) {
        List<Map<String, Object>> constraints = jdbc.queryForList("""
                SELECT cc.name AS name, cc.definition AS definition
                  FROM sys.check_constraints cc
                  JOIN sys.columns c
                    ON c.object_id = cc.parent_object_id AND c.column_id = cc.parent_column_id
                 WHERE cc.parent_object_id = OBJECT_ID(?) AND c.name = ?
                """, table, column);

        List<String> values = Arrays.stream(type.getEnumConstants()).map(Enum::name).toList();

        boolean outdated = constraints.stream().anyMatch(row -> {
            String definition = String.valueOf(row.get("definition"));
            return values.stream().anyMatch(v -> !definition.contains("'" + v + "'"));
        });
        if (!outdated) {
            return;
        }

        for (Map<String, Object> row : constraints) {
            jdbc.execute("ALTER TABLE [" + table + "] DROP CONSTRAINT [" + row.get("name") + "]");
        }
        String allowed = values.stream().map(v -> "'" + v + "'").collect(Collectors.joining(","));
        jdbc.execute("ALTER TABLE [" + table + "] ADD CONSTRAINT [CK_" + table + "_" + column
                + "] CHECK ([" + column + "] IN (" + allowed + "))");

        log.info("Updated CHECK constraint on {}.{} to allow {}", table, column, values);
    }

    private boolean isSqlServer() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            return connection.getMetaData().getDatabaseProductName().toLowerCase().contains("sql server");
        }
    }
}
