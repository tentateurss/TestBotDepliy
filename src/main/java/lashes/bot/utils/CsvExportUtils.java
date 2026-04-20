package lashes.bot.utils;

import lashes.bot.models.Appointment;
import lashes.bot.models.Review;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Утилита для экспорта данных в CSV
 */
public class CsvExportUtils {
    private static final Logger log = LoggerFactory.getLogger(CsvExportUtils.class);
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final String CSV_SEPARATOR = ";";
    
    /**
     * Экспорт записей в CSV файл
     */
    public static File exportAppointments(List<Appointment> appointments, String filename) {
        File file = new File(filename);
        
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            // Заголовки
            writer.write("ID" + CSV_SEPARATOR);
            writer.write("Дата и время" + CSV_SEPARATOR);
            writer.write("Клиент" + CSV_SEPARATOR);
            writer.write("Телефон" + CSV_SEPARATOR);
            writer.write("Услуга" + CSV_SEPARATOR);
            writer.write("Цена" + CSV_SEPARATOR);
            writer.write("Статус" + CSV_SEPARATOR);
            writer.write("Предоплата" + CSV_SEPARATOR);
            writer.write("Создано" + CSV_SEPARATOR);
            writer.newLine();
            
            // Данные
            for (Appointment appt : appointments) {
                writer.write(String.valueOf(appt.getId()) + CSV_SEPARATOR);
                writer.write(appt.getAppointmentTime().format(DATETIME_FORMATTER) + CSV_SEPARATOR);
                writer.write(escapeCsv(appt.getUserName()) + CSV_SEPARATOR);
                writer.write(escapeCsv(appt.getPhone()) + CSV_SEPARATOR);
                writer.write(escapeCsv(appt.getServiceName()) + CSV_SEPARATOR);
                writer.write(String.format("%.2f", appt.getPrice()) + CSV_SEPARATOR);
                writer.write(getStatusText(appt.getStatus()) + CSV_SEPARATOR);
                writer.write(getPrepaymentText(appt) + CSV_SEPARATOR);
                writer.write(appt.getCreatedAt().format(DATETIME_FORMATTER) + CSV_SEPARATOR);
                writer.newLine();
            }
            
            log.info("Exported {} appointments to {}", appointments.size(), filename);
            return file;
            
        } catch (IOException e) {
            log.error("Failed to export appointments to CSV", e);
            return null;
        }
    }
    
    /**
     * Экспорт отзывов в CSV файл
     */
    public static File exportReviews(List<Review> reviews, String filename) {
        File file = new File(filename);
        
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            // Заголовки
            writer.write("ID" + CSV_SEPARATOR);
            writer.write("Дата" + CSV_SEPARATOR);
            writer.write("Клиент" + CSV_SEPARATOR);
            writer.write("Рейтинг" + CSV_SEPARATOR);
            writer.write("Комментарий" + CSV_SEPARATOR);
            writer.newLine();
            
            // Данные
            for (Review review : reviews) {
                writer.write(String.valueOf(review.getId()) + CSV_SEPARATOR);
                writer.write(review.getCreatedAt().format(DATETIME_FORMATTER) + CSV_SEPARATOR);
                writer.write(escapeCsv(review.getUserName()) + CSV_SEPARATOR);
                writer.write(getRatingStars(review.getRating()) + CSV_SEPARATOR);
                writer.write(escapeCsv(review.getComment()) + CSV_SEPARATOR);
                writer.newLine();
            }
            
            log.info("Exported {} reviews to {}", reviews.size(), filename);
            return file;
            
        } catch (IOException e) {
            log.error("Failed to export reviews to CSV", e);
            return null;
        }
    }
    
    /**
     * Экспорт статистики за период в CSV
     */
    public static File exportStatistics(
            LocalDateTime startDate, 
            LocalDateTime endDate,
            int totalAppointments,
            double totalRevenue,
            double averageRating,
            String filename) {
        
        File file = new File(filename);
        
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write("СТАТИСТИКА ЗА ПЕРИОД" + CSV_SEPARATOR);
            writer.newLine();
            writer.newLine();
            
            writer.write("Период" + CSV_SEPARATOR);
            writer.write(startDate.format(DATETIME_FORMATTER) + " - " + endDate.format(DATETIME_FORMATTER));
            writer.newLine();
            
            writer.write("Всего записей" + CSV_SEPARATOR);
            writer.write(String.valueOf(totalAppointments));
            writer.newLine();
            
            writer.write("Общий доход" + CSV_SEPARATOR);
            writer.write(String.format("%.2f ₽", totalRevenue));
            writer.newLine();
            
            writer.write("Средний рейтинг" + CSV_SEPARATOR);
            writer.write(String.format("%.1f", averageRating));
            writer.newLine();
            
            log.info("Exported statistics to {}", filename);
            return file;
            
        } catch (IOException e) {
            log.error("Failed to export statistics to CSV", e);
            return null;
        }
    }
    
    /**
     * Экранирование специальных символов для CSV
     */
    private static String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        
        // Если содержит разделитель, кавычки или перенос строки - оборачиваем в кавычки
        if (value.contains(CSV_SEPARATOR) || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        
        return value;
    }
    
    /**
     * Получить текст статуса
     */
    private static String getStatusText(String status) {
        switch (status) {
            case "active": return "Активна";
            case "cancelled": return "Отменена";
            case "completed": return "Завершена";
            default: return status;
        }
    }
    
    /**
     * Получить текст предоплаты
     */
    private static String getPrepaymentText(Appointment appt) {
        if (appt.getPrepaymentAmount() == null || appt.getPrepaymentAmount() == 0) {
            return "Нет";
        }
        
        String confirmed = appt.getPrepaymentConfirmed() ? "✓" : "✗";
        return String.format("%.2f ₽ %s", appt.getPrepaymentAmount(), confirmed);
    }
    
    /**
     * Получить звезды рейтинга
     */
    private static String getRatingStars(int rating) {
        StringBuilder stars = new StringBuilder();
        for (int i = 0; i < rating; i++) {
            stars.append("★");
        }
        for (int i = rating; i < 5; i++) {
            stars.append("☆");
        }
        return stars.toString();
    }
    
    /**
     * Генерация имени файла с текущей датой
     */
    public static String generateFilename(String prefix) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        return String.format("%s_%s.csv", prefix, timestamp);
    }
}
