package lashes.bot.config;

import java.io.InputStream;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

public class BotConfig {
    private static final Properties props = new Properties();

    static {
        try (InputStream input = BotConfig.class.getClassLoader()
                .getResourceAsStream("application.properties")) {
            props.load(input);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load config", e);
        }
    }

    public static String getBotToken() {
        return props.getProperty("bot.token");
    }

    public static String getBotUsername() {
        return props.getProperty("bot.username");
    }

    /**
     * @deprecated Используйте {@link #isAdmin(Long)} или {@link #getAdminIds()}
     */
    @Deprecated
    public static Long getAdminId() {
        String adminId = props.getProperty("bot.admin.id");
        if (adminId != null && !adminId.contains(",")) {
            return Long.parseLong(adminId);
        }
        return getAdminIds().isEmpty() ? null : getAdminIds().get(0);
    }

    public static List<Long> getAdminIds() {
        String adminIds = props.getProperty("bot.admin.id");
        if (adminIds == null || adminIds.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(adminIds.split(","))
                .map(String::trim)
                .map(Long::parseLong)
                .collect(Collectors.toList());
    }

    public static boolean isAdmin(Long userId) {
        return getAdminIds().contains(userId);
    }
    
    /**
     * Получить список супер-админов (разработчиков)
     * Супер-админы имеют доступ ко всем ботам в системе
     */
    public static List<Long> getSuperAdminIds() {
        String superAdmins = props.getProperty("bot.super.admins");
        if (superAdmins == null || superAdmins.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(superAdmins.split(","))
                .map(String::trim)
                .map(Long::parseLong)
                .collect(Collectors.toList());
    }
    
    /**
     * Проверить, является ли пользователь супер-админом
     */
    public static boolean isSuperAdmin(Long userId) {
        return getSuperAdminIds().contains(userId);
    }
    
    /**
     * Проверить, имеет ли пользователь админ-доступ (обычный админ или супер-админ)
     */
    public static boolean hasAdminAccess(Long userId) {
        return isAdmin(userId) || isSuperAdmin(userId);
    }

    public static Long getChannelId() {
        String id = props.getProperty("bot.channel.id");
        if (id == null || id.trim().isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(id.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String getChannelLink() {
        return props.getProperty("bot.channel.link");
    }

    public static String getChannelUsername() {
        return props.getProperty("bot.channel.username");
    }

    // ============ ВСТАВЬТЕ СЮДА ID КАНАЛА С ОТЗЫВАМИ ============
    // Получить ID канала: добавьте бота в канал, перешлите сообщение из канала боту @userinfobot
    public static Long getReviewsChannelId() {
        String id = props.getProperty("bot.reviews.channel.id");
        if (id == null || id.trim().isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(id.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ============ ВСТАВЬТЕ СЮДА ССЫЛКУ НА КАНАЛ С ОТЗЫВАМИ ============
    // Формат: https://t.me/your_reviews_channel
    public static String getReviewsChannelLink() {
        return props.getProperty("bot.reviews.channel.link");
    }

    public static String getDatabasePath() {
        return props.getProperty("database.path");
    }
    
    /**
     * Получить часовой пояс для уведомлений
     * По умолчанию: Europe/Samara (UTC+4)
     */
    public static ZoneId getTimezone() {
        String timezone = props.getProperty("bot.timezone", "Europe/Samara");
        try {
            return ZoneId.of(timezone);
        } catch (Exception e) {
            // Если указан неверный часовой пояс, используем Самару
            return ZoneId.of("Europe/Samara");
        }
    }
}