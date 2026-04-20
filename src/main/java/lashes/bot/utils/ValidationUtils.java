package lashes.bot.utils;

import java.util.regex.Pattern;

/**
 * Утилита для валидации входных данных
 */
public class ValidationUtils {
    
    // Паттерны для валидации
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[0-9]{10,15}$");
    private static final Pattern TIME_PATTERN = Pattern.compile("^([0-1]?[0-9]|2[0-3]):[0-5][0-9]$");
    private static final Pattern NAME_PATTERN = Pattern.compile("^[а-яА-ЯёЁa-zA-Z\\s-]{2,50}$");
    
    /**
     * Валидация номера телефона
     */
    public static boolean isValidPhone(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return false;
        }
        String cleaned = phone.replaceAll("[\\s()-]", "");
        return PHONE_PATTERN.matcher(cleaned).matches();
    }
    
    /**
     * Валидация времени (формат HH:mm)
     */
    public static boolean isValidTime(String time) {
        if (time == null || time.trim().isEmpty()) {
            return false;
        }
        return TIME_PATTERN.matcher(time.trim()).matches();
    }
    
    /**
     * Валидация имени
     */
    public static boolean isValidName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        return NAME_PATTERN.matcher(name.trim()).matches();
    }
    
    /**
     * Валидация процента (0-100)
     */
    public static boolean isValidPercent(int percent) {
        return percent >= 0 && percent <= 100;
    }
    
    /**
     * Валидация цены (положительное число)
     */
    public static boolean isValidPrice(double price) {
        return price > 0 && price < 1000000;
    }
    
    /**
     * Валидация рейтинга (1-5)
     */
    public static boolean isValidRating(int rating) {
        return rating >= 1 && rating <= 5;
    }
    
    /**
     * Очистка номера телефона (удаление пробелов, скобок, дефисов)
     */
    public static String cleanPhone(String phone) {
        if (phone == null) {
            return null;
        }
        return phone.replaceAll("[\\s()-]", "");
    }
    
    /**
     * Очистка имени (trim и удаление лишних пробелов)
     */
    public static String cleanName(String name) {
        if (name == null) {
            return null;
        }
        return name.trim().replaceAll("\\s+", " ");
    }
    
    /**
     * Валидация длины текста
     */
    public static boolean isValidTextLength(String text, int minLength, int maxLength) {
        if (text == null) {
            return false;
        }
        int length = text.trim().length();
        return length >= minLength && length <= maxLength;
    }
    
    /**
     * Валидация комментария к отзыву
     */
    public static boolean isValidReviewComment(String comment) {
        return comment != null && isValidTextLength(comment, 10, 1000);
    }
    
    /**
     * Валидация дополнительной информации профиля
     */
    public static boolean isValidAdditionalInfo(String info) {
        if (info == null || info.trim().isEmpty()) {
            return true; // Дополнительная информация необязательна
        }
        return isValidTextLength(info, 0, 500);
    }
}
