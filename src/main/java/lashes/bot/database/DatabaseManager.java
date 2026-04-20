package lashes.bot.database;

import lashes.bot.config.BotConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.*;

public class DatabaseManager {
    private static final Logger log = LoggerFactory.getLogger(DatabaseManager.class);
    private static final String DB_PATH = BotConfig.getDatabasePath();
    private static Connection connection;

    static {
        initialize();
    }

    private static void initialize() {
        try {
            File dbFile = new File(DB_PATH);
            File parentDir = dbFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            Class.forName("org.sqlite.JDBC");

            connection = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);

            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA busy_timeout = 30000");
            }

            // Используем Flyway для миграций вместо ручного создания таблиц
            FlywayMigration.migrate(DB_PATH);
            
            String version = FlywayMigration.getCurrentVersion(DB_PATH);
            log.info("Database initialized at: {} (version: {})", DB_PATH, version);
        } catch (Exception e) {
            log.error("Failed to initialize database", e);
            throw new RuntimeException(e);
        }
    }

    public static synchronized Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                connection = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute("PRAGMA busy_timeout = 30000");
                }
            }
            return connection;
        } catch (SQLException e) {
            log.error("Failed to get connection", e);
            throw new RuntimeException("Failed to get database connection", e);
        }
    }

    public static void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            log.error("Failed to close connection", e);
        }
    }
}
