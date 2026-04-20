package lashes.bot.database;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Управление миграциями базы данных с помощью Flyway
 */
public class FlywayMigration {
    private static final Logger log = LoggerFactory.getLogger(FlywayMigration.class);

    /**
     * Выполняет миграции базы данных
     * @param databasePath путь к файлу базы данных
     */
    public static void migrate(String databasePath) {
        try {
            log.info("Starting database migration...");
            
            String jdbcUrl = "jdbc:sqlite:" + databasePath;
            
            Flyway flyway = Flyway.configure()
                    .dataSource(jdbcUrl, null, null)
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(true) // Для существующих БД
                    .baselineVersion("0") // Начальная версия
                    .validateOnMigrate(true)
                    .load();
            
            // Выполняем миграции
            int migrationsApplied = flyway.migrate().migrationsExecuted;
            
            if (migrationsApplied > 0) {
                log.info("✅ Applied {} database migration(s)", migrationsApplied);
            } else {
                log.info("✅ Database is up to date");
            }
            
        } catch (Exception e) {
            log.error("❌ Database migration failed", e);
            throw new RuntimeException("Failed to migrate database", e);
        }
    }
    
    /**
     * Получает информацию о текущей версии БД
     * @param databasePath путь к файлу базы данных
     * @return версия БД
     */
    public static String getCurrentVersion(String databasePath) {
        try {
            String jdbcUrl = "jdbc:sqlite:" + databasePath;
            
            Flyway flyway = Flyway.configure()
                    .dataSource(jdbcUrl, null, null)
                    .locations("classpath:db/migration")
                    .load();
            
            var info = flyway.info();
            var current = info.current();
            
            if (current != null) {
                return current.getVersion().toString();
            }
            return "No migrations applied";
            
        } catch (Exception e) {
            log.error("Failed to get database version", e);
            return "Unknown";
        }
    }
}
