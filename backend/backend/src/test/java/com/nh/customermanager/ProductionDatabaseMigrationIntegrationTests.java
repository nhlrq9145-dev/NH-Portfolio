package com.nh.customermanager;

import org.junit.jupiter.api.Test;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:production_database_migration_test;MODE=MySQL;IGNORECASE=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=migration_test_only_user",
        "spring.datasource.password=migration_test_only_password",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.show-sql=false",
        "ADMIN_USERNAME=migration-test-only-admin",
        "ADMIN_PASSWORD=migration-test-only-admin-password-2026"
})
@ActiveProfiles("prod")
class ProductionDatabaseMigrationIntegrationTests {

    private static final Set<String> ADMIN_USER_COLUMNS = Set.of(
            "id",
            "username",
            "password_hash",
            "enabled",
            "created_at"
    );

    private static final Set<String> CUSTOMER_COLUMNS = Set.of(
            "id",
            "name",
            "phone",
            "email",
            "status",
            "created_at"
    );

    private static final Set<String> UNIQUE_CONSTRAINTS = Set.of(
            "uk_admin_users_username",
            "uk_customers_email",
            "uk_customers_phone"
    );

    private static final String MYSQL_EMAIL_COLLATION_DEFINITION =
            "email VARCHAR(150) "
                    + "/*!80000 CHARACTER SET utf8mb4 "
                    + "COLLATE utf8mb4_0900_ai_ci */";

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Flyway flyway;

    @Test
    void appliesOnlyVersionOneToIsolatedH2Database()
            throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            String url = connection.getMetaData().getURL();
            assertTrue(url.startsWith(
                    "jdbc:h2:mem:production_database_migration_test"
            ));
            assertFalse(url.startsWith("jdbc:mysql:"));

            assertEquals(
                    1,
                    queryForCount(
                            connection,
                            """
                                    SELECT COUNT(*)
                                    FROM "flyway_schema_history"
                                    WHERE "version" = '1'
                                      AND "success" = TRUE
                                    """
                    )
            );
            assertEquals(
                    1,
                    queryForCount(
                            connection,
                            """
                                    SELECT COUNT(*)
                                    FROM "flyway_schema_history"
                                    WHERE "version" IS NOT NULL
                                      AND "success" = TRUE
                                    """
                    )
            );

            assertEquals(0, flyway.migrate().migrationsExecuted);
            assertEquals(
                    1,
                    queryForCount(
                            connection,
                            """
                                    SELECT COUNT(*)
                                    FROM "flyway_schema_history"
                                    WHERE "version" IS NOT NULL
                                      AND "success" = TRUE
                                    """
                    )
            );
        }
    }

    @Test
    void createsSchemaMatchingCurrentEntitiesAndNamedConstraints()
            throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            Map<String, ColumnMetadata> adminColumns = readColumns(
                    connection,
                    "admin_users"
            );
            Map<String, ColumnMetadata> customerColumns = readColumns(
                    connection,
                    "customers"
            );

            assertEquals(ADMIN_USER_COLUMNS, adminColumns.keySet());
            assertEquals(CUSTOMER_COLUMNS, customerColumns.keySet());

            assertColumn(adminColumns, "id", false, null, true);
            assertColumn(adminColumns, "username", false, 50L, false);
            assertColumn(
                    adminColumns,
                    "password_hash",
                    false,
                    100L,
                    false
            );
            assertColumn(adminColumns, "enabled", false, null, false);
            assertColumn(adminColumns, "created_at", false, null, false);

            assertColumn(customerColumns, "id", false, null, true);
            assertColumn(customerColumns, "name", false, 100L, false);
            assertColumn(customerColumns, "phone", true, 30L, false);
            assertColumn(customerColumns, "email", true, 150L, false);
            assertColumn(customerColumns, "status", false, 30L, false);
            assertColumn(
                    customerColumns,
                    "created_at",
                    false,
                    null,
                    false
            );

            assertEquals(Set.of("id"), readPrimaryKeyColumns(
                    connection,
                    "admin_users"
            ));
            assertEquals(Set.of("id"), readPrimaryKeyColumns(
                    connection,
                    "customers"
            ));
            assertEquals(
                    UNIQUE_CONSTRAINTS,
                    readUniqueConstraints(connection)
            );
        }
    }

    @Test
    void pinsMySqlEmailCollationAndRejectsCaseVariants()
            throws Exception {
        String migrationSql = readMigrationSql().replaceAll("\\s+", " ");
        assertTrue(
                migrationSql.contains(MYSQL_EMAIL_COLLATION_DEFINITION),
                "V1 must explicitly pin customers.email to "
                        + "utf8mb4_0900_ai_ci"
        );

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                insertCustomer(connection, "User@example.com");
                SQLException duplicate = assertThrows(
                        SQLException.class,
                        () -> insertCustomer(
                                connection,
                                "user@example.com"
                        )
                );
                assertEquals("23505", duplicate.getSQLState());
            } finally {
                connection.rollback();
            }
        }
    }

    private String readMigrationSql() throws IOException {
        ClassPathResource migration = new ClassPathResource(
                "db/migration/V1__create_customer_manager_schema.sql"
        );
        try (InputStream inputStream = migration.getInputStream()) {
            return new String(
                    inputStream.readAllBytes(),
                    StandardCharsets.UTF_8
            );
        }
    }

    private void insertCustomer(
            Connection connection,
            String email
    ) throws SQLException {
        String sql = """
                INSERT INTO customers (
                    name,
                    phone,
                    email,
                    status,
                    created_at
                ) VALUES (
                    'Migration test customer',
                    NULL,
                    ?,
                    'ACTIVE',
                    CURRENT_TIMESTAMP
                )
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, email);
            statement.executeUpdate();
        }
    }

    private int queryForCount(
            Connection connection,
            String sql
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private Map<String, ColumnMetadata> readColumns(
            Connection connection,
            String tableName
    ) throws SQLException {
        Map<String, ColumnMetadata> columns = new HashMap<>();
        String sql = """
                SELECT COLUMN_NAME,
                       IS_NULLABLE,
                       CHARACTER_MAXIMUM_LENGTH,
                       IS_IDENTITY
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE UPPER(TABLE_SCHEMA) = 'PUBLIC'
                  AND UPPER(TABLE_NAME) = UPPER(?)
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Number maximumLength = (Number) resultSet.getObject(
                            "CHARACTER_MAXIMUM_LENGTH"
                    );
                    columns.put(
                            normalize(resultSet.getString("COLUMN_NAME")),
                            new ColumnMetadata(
                                    "YES".equals(resultSet.getString(
                                            "IS_NULLABLE"
                                    )),
                                    maximumLength == null
                                            ? null
                                            : maximumLength.longValue(),
                                    "YES".equals(resultSet.getString(
                                            "IS_IDENTITY"
                                    ))
                            )
                    );
                }
            }
        }

        return columns;
    }

    private Set<String> readPrimaryKeyColumns(
            Connection connection,
            String tableName
    ) throws SQLException {
        Set<String> columns = new HashSet<>();
        try (ResultSet resultSet = connection.getMetaData().getPrimaryKeys(
                null,
                null,
                tableName.toUpperCase(Locale.ROOT)
        )) {
            while (resultSet.next()) {
                columns.add(normalize(resultSet.getString("COLUMN_NAME")));
            }
        }
        return columns;
    }

    private Set<String> readUniqueConstraints(
            Connection connection
    ) throws SQLException {
        Set<String> constraints = new HashSet<>();
        String sql = """
                SELECT CONSTRAINT_NAME
                FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
                WHERE UPPER(TABLE_SCHEMA) = 'PUBLIC'
                  AND UPPER(TABLE_NAME) IN ('ADMIN_USERS', 'CUSTOMERS')
                  AND CONSTRAINT_TYPE = 'UNIQUE'
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                constraints.add(normalize(resultSet.getString(
                        "CONSTRAINT_NAME"
                )));
            }
        }
        return constraints;
    }

    private void assertColumn(
            Map<String, ColumnMetadata> columns,
            String name,
            boolean nullable,
            Long maximumLength,
            boolean identity
    ) {
        assertEquals(
                new ColumnMetadata(nullable, maximumLength, identity),
                columns.get(name)
        );
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private record ColumnMetadata(
            boolean nullable,
            Long maximumLength,
            boolean identity
    ) {
    }
}
