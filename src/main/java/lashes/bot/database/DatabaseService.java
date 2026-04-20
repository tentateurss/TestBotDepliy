package lashes.bot.database;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lashes.bot.models.Appointment;
import lashes.bot.models.UserProfile;
import lashes.bot.models.WorkDay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class DatabaseService {
    private static final Logger log = LoggerFactory.getLogger(DatabaseService.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    // ============ Work Days ============

    public static void saveWorkDay(WorkDay workDay) {
        String sql = "INSERT OR REPLACE INTO work_days (date, is_working, time_slots) VALUES (?, ?, ?)";
        DatabaseUtils.executeUpdate(sql,
                workDay.getDate().format(DATE_FORMATTER),
                workDay.isWorking() ? 1 : 0,
                workDay.getSlots()
        );
    }

    public static Optional<WorkDay> getWorkDay(LocalDate date) {
        String sql = "SELECT * FROM work_days WHERE date = ?";
        return DatabaseUtils.executeQuerySingle(sql, rs -> {
            WorkDay wd = new WorkDay();
            wd.setId(rs.getLong("id"));
            wd.setDate(LocalDate.parse(rs.getString("date"), DATE_FORMATTER));
            wd.setWorking(rs.getInt("is_working") == 1);
            wd.setSlots(rs.getString("time_slots")); // Исправлено: было slots, стало time_slots
            return wd;
        }, date.format(DATE_FORMATTER));
    }

    /**
     * Получить все рабочие дни в диапазоне дат
     */
    public static List<LocalDate> getWorkingDays(LocalDate from, LocalDate to) {
        String sql = "SELECT date FROM work_days WHERE date >= ? AND date <= ? AND is_working = 1 ORDER BY date";
        List<LocalDate> result = DatabaseUtils.executeQueryList(sql,
                rs -> LocalDate.parse(rs.getString("date"), DATE_FORMATTER),
                from.format(DATE_FORMATTER),
                to.format(DATE_FORMATTER)
        );
        return result;
    }

    public static List<String> getAvailableSlots(LocalDate date) {
        Optional<WorkDay> workDayOpt = getWorkDay(date);
        if (workDayOpt.isEmpty() || !workDayOpt.get().isWorking()) {
            return Collections.emptyList();
        }

        try {
            List<String> allSlots = mapper.readValue(workDayOpt.get().getSlots(), new TypeReference<List<String>>() {});

            String sql = "SELECT appointment_time FROM appointments WHERE date(appointment_time) = ? AND status = 'active'";
            Set<String> bookedSlots = new HashSet<>();

            List<LocalDateTime> bookedTimes = DatabaseUtils.executeQueryList(sql,
                    rs -> LocalDateTime.parse(rs.getString("appointment_time"), DATETIME_FORMATTER),
                    date.format(DATE_FORMATTER)
            );

            for (LocalDateTime dt : bookedTimes) {
                bookedSlots.add(dt.toLocalTime().toString().substring(0, 5));
            }

            List<String> available = new ArrayList<>(allSlots);
            available.removeIf(bookedSlots::contains);
            return available;

        } catch (Exception e) {
            log.error("Failed to parse slots", e);
            return Collections.emptyList();
        }
    }

    public static void updateSlots(LocalDate date, List<String> slots) {
        try {
            String sql = "UPDATE work_days SET slots = ? WHERE date = ?";
            DatabaseUtils.executeUpdate(sql, mapper.writeValueAsString(slots), date.format(DATE_FORMATTER));
        } catch (Exception e) {
            log.error("Failed to update slots", e);
        }
    }

    public static void addSlotToDay(LocalDate date, String newSlot) {
        Optional<WorkDay> opt = getWorkDay(date);
        if (opt.isEmpty()) {
            log.warn("Work day not found for date {}", date);
            return;
        }
        try {
            List<String> slots = new ArrayList<>(mapper.readValue(opt.get().getSlots(), new TypeReference<List<String>>() {}));
            if (!slots.contains(newSlot)) {
                slots.add(newSlot);
                slots.sort(String::compareTo);
                updateSlots(date, slots);
            }
        } catch (Exception e) {
            log.error("Failed to add slot", e);
        }
    }

    public static void closeDay(LocalDate date) {
        String sql = "UPDATE work_days SET is_working = 0 WHERE date = ?";
        DatabaseUtils.executeUpdate(sql, date.format(DATE_FORMATTER));
    }

    // ============ Appointments ============

    public static Long saveAppointment(Appointment appt) {
        String sql = "INSERT INTO appointments (user_id, user_name, phone, appointment_time, status, created_at, reminder_job_key) VALUES (?, ?, ?, ?, ?, ?, ?)";
        return DatabaseUtils.executeInsert(sql,
                appt.getUserId(),
                appt.getUserName(),
                appt.getPhone(),
                appt.getAppointmentTime().format(DATETIME_FORMATTER),
                appt.getStatus(),
                appt.getCreatedAt().format(DATETIME_FORMATTER),
                appt.getReminderJobKey()
        );
    }

    public static Optional<Appointment> getAppointment(Long id) {
        String sql = "SELECT * FROM appointments WHERE id = ?";
        return DatabaseUtils.executeQuerySingle(sql, rs -> mapAppointment(rs), id);
    }

    public static Optional<Appointment> getActiveAppointmentByUser(Long userId) {
        String sql = "SELECT * FROM appointments WHERE user_id = ? AND status = 'active' ORDER BY created_at DESC LIMIT 1";
        return DatabaseUtils.executeQuerySingle(sql, rs -> mapAppointment(rs), userId);
    }

    public static List<Appointment> getAppointmentsByDate(LocalDate date) {
        String sql = "SELECT * FROM appointments WHERE date(appointment_time) = ? AND status = 'active' ORDER BY appointment_time";
        return DatabaseUtils.executeQueryList(sql, rs -> mapAppointment(rs), date.format(DATE_FORMATTER));
    }

    public static List<Appointment> getAllActiveAppointments() {
        String sql = "SELECT * FROM appointments WHERE status = 'active' ORDER BY appointment_time";
        return DatabaseUtils.executeQueryList(sql, rs -> mapAppointment(rs));
    }

    public static void cancelAppointment(Long id) {
        String sql = "UPDATE appointments SET status = 'cancelled' WHERE id = ?";
        DatabaseUtils.executeUpdate(sql, id);
    }
    
    public static void completeAppointment(Long id) {
        String sql = "UPDATE appointments SET status = 'completed' WHERE id = ?";
        DatabaseUtils.executeUpdate(sql, id);
    }

    public static void updateReminderJobKey(Long id, String jobKey) {
        String sql = "UPDATE appointments SET reminder_job_key = ? WHERE id = ?";
        DatabaseUtils.executeUpdate(sql, jobKey, id);
    }

    private static Appointment mapAppointment(ResultSet rs) throws SQLException {
        Appointment appt = new Appointment();
        appt.setId(rs.getLong("id"));
        appt.setUserId(rs.getLong("user_id"));
        appt.setUserName(rs.getString("user_name"));
        appt.setPhone(rs.getString("phone"));
        appt.setAppointmentTime(LocalDateTime.parse(rs.getString("appointment_time"), DATETIME_FORMATTER));
        appt.setStatus(rs.getString("status"));
        appt.setCreatedAt(LocalDateTime.parse(rs.getString("created_at"), DATETIME_FORMATTER));
        appt.setReminderJobKey(rs.getString("reminder_job_key"));
        
        try {
            appt.setReminder3hJobKey(rs.getString("reminder_3h_job_key"));
        } catch (SQLException e) {
            // Колонка может не существовать в старой БД
        }
        
        try {
            appt.setPrice(rs.getDouble("price"));
        } catch (SQLException e) {
            appt.setPrice(0.0);
        }
        
        try {
            appt.setCompleted(rs.getInt("completed"));
        } catch (SQLException e) {
            appt.setCompleted(0);
        }
        
        try {
            appt.setServiceName(rs.getString("service_name"));
        } catch (SQLException e) {
            // Колонка может не существовать
        }
        
        try {
            appt.setPrepaymentAmount(rs.getDouble("prepayment_amount"));
        } catch (SQLException e) {
            appt.setPrepaymentAmount(0.0);
        }
        
        try {
            appt.setPrepaymentScreenshotId(rs.getString("prepayment_screenshot_id"));
        } catch (SQLException e) {
            // Колонка может не существовать
        }
        
        try {
            appt.setPrepaymentConfirmed(rs.getInt("prepayment_confirmed") == 1);
        } catch (SQLException e) {
            appt.setPrepaymentConfirmed(false);
        }
        
        return appt;
    }

    // ============ Subscriptions ============

    public static void setUserSubscribed(Long userId, boolean subscribed) {
        String sql = "INSERT OR REPLACE INTO user_subscriptions (user_id, is_subscribed, last_check) VALUES (?, ?, ?)";
        DatabaseUtils.executeUpdate(sql, userId, subscribed ? 1 : 0, LocalDateTime.now().format(DATETIME_FORMATTER));
    }

    public static boolean isUserSubscribed(Long userId) {
        String sql = "SELECT is_subscribed FROM user_subscriptions WHERE user_id = ?";
        Optional<Integer> result = DatabaseUtils.executeQuerySingle(sql, rs -> rs.getInt("is_subscribed"), userId);
        return result.orElse(0) == 1;
    }

    // ============ Price / Settings ============

    /**
     * Получить значение настройки
     */
    public static Optional<String> getSetting(String key) {
        String sql = "SELECT value FROM settings WHERE key = ?";
        return DatabaseUtils.executeQuerySingle(sql, rs -> rs.getString("value"), key);
    }

    /**
     * Сохранить/обновить настройку
     */
    public static void saveSetting(String key, String value) {
        String sql = "INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)";
        DatabaseUtils.executeUpdate(sql, key, value);
    }

    // ============ Reviews ============

    public static Long saveReview(Long appointmentId, Long userId, String userName, Integer rating, String comment) {
        String sql = "INSERT INTO reviews (appointment_id, user_id, user_name, rating, comment, created_at) VALUES (?, ?, ?, ?, ?, ?)";
        return DatabaseUtils.executeInsert(sql, appointmentId, userId, userName, rating, comment, LocalDateTime.now().format(DATETIME_FORMATTER));
    }

    public static List<Map<String, Object>> getAllReviews() {
        String sql = "SELECT * FROM reviews ORDER BY created_at DESC";
        return DatabaseUtils.executeQueryList(sql, rs -> {
            Map<String, Object> review = new HashMap<>();
            review.put("id", rs.getLong("id"));
            review.put("appointmentId", rs.getLong("appointment_id"));
            review.put("userId", rs.getLong("user_id"));
            review.put("userName", rs.getString("user_name"));
            review.put("rating", rs.getInt("rating"));
            review.put("comment", rs.getString("comment"));
            review.put("createdAt", LocalDateTime.parse(rs.getString("created_at"), DATETIME_FORMATTER));
            return review;
        });
    }

    public static boolean hasReviewForAppointment(Long appointmentId) {
        String sql = "SELECT COUNT(*) as cnt FROM reviews WHERE appointment_id = ?";
        Optional<Integer> result = DatabaseUtils.executeQuerySingle(sql, rs -> rs.getInt("cnt"), appointmentId);
        return result.orElse(0) > 0;
    }

    // ============ Waiting List ============

    public static Long addToWaitingList(Long userId, String userName, String phone, LocalDate date, String timeSlot) {
        String sql = "INSERT INTO waiting_list (user_id, user_name, phone, date, time_slot, created_at) VALUES (?, ?, ?, ?, ?, ?)";
        return DatabaseUtils.executeInsert(sql, userId, userName, phone, date.format(DATE_FORMATTER), timeSlot, LocalDateTime.now().format(DATETIME_FORMATTER));
    }

    public static List<Map<String, Object>> getWaitingListForSlot(LocalDate date, String timeSlot) {
        String sql = "SELECT * FROM waiting_list WHERE date = ? AND time_slot = ? ORDER BY created_at";
        return DatabaseUtils.executeQueryList(sql, rs -> {
            Map<String, Object> entry = new HashMap<>();
            entry.put("id", rs.getLong("id"));
            entry.put("userId", rs.getLong("user_id"));
            entry.put("userName", rs.getString("user_name"));
            entry.put("phone", rs.getString("phone"));
            entry.put("date", LocalDate.parse(rs.getString("date"), DATE_FORMATTER));
            entry.put("timeSlot", rs.getString("time_slot"));
            entry.put("createdAt", LocalDateTime.parse(rs.getString("created_at"), DATETIME_FORMATTER));
            return entry;
        }, date.format(DATE_FORMATTER), timeSlot);
    }

    public static void removeFromWaitingList(Long id) {
        String sql = "DELETE FROM waiting_list WHERE id = ?";
        DatabaseUtils.executeUpdate(sql, id);
    }

    // ============ Statistics ============

    public static Map<String, Object> getMonthlyStatistics(int year, int month) {
        Map<String, Object> stats = new HashMap<>();
        
        String startDate = String.format("%04d-%02d-01", year, month);
        String endDate = month == 12 ? String.format("%04d-01-01", year + 1) : String.format("%04d-%02d-01", year, month + 1);
        
        // Количество записей за месяц
        String countSql = "SELECT COUNT(*) as cnt FROM appointments WHERE date(appointment_time) >= ? AND date(appointment_time) < ? AND status = 'active'";
        Optional<Integer> count = DatabaseUtils.executeQuerySingle(countSql, rs -> rs.getInt("cnt"), startDate, endDate);
        stats.put("totalAppointments", count.orElse(0));
        
        // Доход за месяц
        String revenueSql = "SELECT SUM(price) as total FROM appointments WHERE date(appointment_time) >= ? AND date(appointment_time) < ? AND status = 'active'";
        Optional<Double> revenue = DatabaseUtils.executeQuerySingle(revenueSql, rs -> rs.getDouble("total"), startDate, endDate);
        stats.put("totalRevenue", revenue.orElse(0.0));
        
        // Самые популярные слоты
        String slotsSql = "SELECT strftime('%H:%M', appointment_time) as slot, COUNT(*) as cnt FROM appointments " +
                         "WHERE date(appointment_time) >= ? AND date(appointment_time) < ? AND status = 'active' " +
                         "GROUP BY slot ORDER BY cnt DESC LIMIT 5";
        List<Map<String, Object>> popularSlots = DatabaseUtils.executeQueryList(slotsSql, rs -> {
            Map<String, Object> slot = new HashMap<>();
            slot.put("time", rs.getString("slot"));
            slot.put("count", rs.getInt("cnt"));
            return slot;
        }, startDate, endDate);
        stats.put("popularSlots", popularSlots);
        
        return stats;
    }

    public static void updateAppointmentPrice(Long id, Double price) {
        String sql = "UPDATE appointments SET price = ? WHERE id = ?";
        DatabaseUtils.executeUpdate(sql, price, id);
    }

    public static void markAppointmentCompleted(Long id) {
        String sql = "UPDATE appointments SET completed = 1 WHERE id = ?";
        DatabaseUtils.executeUpdate(sql, id);
    }

    public static void updateReminder3hJobKey(Long id, String jobKey) {
        String sql = "UPDATE appointments SET reminder_3h_job_key = ? WHERE id = ?";
        DatabaseUtils.executeUpdate(sql, jobKey, id);
    }

    public static List<Appointment> getAppointmentsByUser(Long userId) {
        String sql = "SELECT * FROM appointments WHERE user_id = ? ORDER BY appointment_time DESC";
        return DatabaseUtils.executeQueryList(sql, rs -> mapAppointment(rs), userId);
    }

    public static List<Long> getAllUserIdsWithActiveAppointments() {
        String sql = "SELECT DISTINCT user_id FROM appointments WHERE status = 'active'";
        return DatabaseUtils.executeQueryList(sql, rs -> rs.getLong("user_id"));
    }

    // ============ Prepayment ============

    public static Optional<Appointment> getAppointmentById(Long id) {
        String sql = "SELECT * FROM appointments WHERE id = ?";
        return DatabaseUtils.executeQuerySingle(sql, rs -> mapAppointment(rs), id);
    }

    public static void updatePrepaymentScreenshot(Long id, String screenshotId) {
        String sql = "UPDATE appointments SET prepayment_screenshot_id = ? WHERE id = ?";
        DatabaseUtils.executeUpdate(sql, screenshotId, id);
    }

    public static void confirmPrepayment(Long id) {
        String sql = "UPDATE appointments SET prepayment_confirmed = 1 WHERE id = ?";
        DatabaseUtils.executeUpdate(sql, id);
    }

    public static void updateAppointmentService(Long id, String serviceName, Double price, Double prepaymentAmount) {
        String sql = "UPDATE appointments SET service_name = ?, price = ?, prepayment_amount = ? WHERE id = ?";
        DatabaseUtils.executeUpdate(sql, serviceName, price, prepaymentAmount, id);
    }

    // ============ User Profiles ============

    public static void saveOrUpdateProfile(UserProfile profile) {
        String sql = "INSERT OR REPLACE INTO user_profiles (user_id, name, phone, additional_info, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)";
        DatabaseUtils.executeUpdate(sql,
                profile.getUserId(),
                profile.getName(),
                profile.getPhone(),
                profile.getAdditionalInfo(),
                profile.getCreatedAt().format(DATETIME_FORMATTER),
                profile.getUpdatedAt().format(DATETIME_FORMATTER)
        );
    }

    public static Optional<UserProfile> getUserProfile(Long userId) {
        String sql = "SELECT * FROM user_profiles WHERE user_id = ?";
        Optional<UserProfile> profile = DatabaseUtils.executeQuerySingle(sql, rs -> {
            UserProfile p = new UserProfile();
            p.setUserId(rs.getLong("user_id"));
            p.setName(rs.getString("name"));
            p.setPhone(rs.getString("phone"));
            p.setAdditionalInfo(rs.getString("additional_info"));
            p.setCreatedAt(LocalDateTime.parse(rs.getString("created_at"), DATETIME_FORMATTER));
            p.setUpdatedAt(LocalDateTime.parse(rs.getString("updated_at"), DATETIME_FORMATTER));
            return p;
        }, userId);

        // Загружаем коды записи
        if (profile.isPresent()) {
            List<String> codes = getBookingCodesByUser(userId);
            profile.get().setBookingCodes(codes);
        }

        return profile;
    }

    public static void deleteUserProfile(Long userId) {
        // Удаляем коды записи
        String deleteCodesSql = "DELETE FROM booking_codes WHERE user_id = ?";
        DatabaseUtils.executeUpdate(deleteCodesSql, userId);
        
        // Удаляем профиль
        String deleteProfileSql = "DELETE FROM user_profiles WHERE user_id = ?";
        DatabaseUtils.executeUpdate(deleteProfileSql, userId);
        
        log.info("Profile and booking codes deleted for user {}", userId);
    }

    // ============ Booking Codes ============

    public static String generateUniqueBookingCode() {
        String code;
        int attempts = 0;
        do {
            code = generateRandomCode();
            attempts++;
            if (attempts > 100) {
                throw new RuntimeException("Failed to generate unique booking code after 100 attempts");
            }
        } while (isBookingCodeExists(code));
        return code;
    }

    private static String generateRandomCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random random = new Random();
        StringBuilder code = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            code.append(chars.charAt(random.nextInt(chars.length())));
        }
        return code.toString();
    }

    private static boolean isBookingCodeExists(String code) {
        String sql = "SELECT COUNT(*) as cnt FROM booking_codes WHERE booking_code = ?";
        Optional<Integer> result = DatabaseUtils.executeQuerySingle(sql, rs -> rs.getInt("cnt"), code);
        return result.orElse(0) > 0;
    }

    public static void saveBookingCode(Long userId, String bookingCode, Long appointmentId) {
        String sql = "INSERT INTO booking_codes (user_id, booking_code, appointment_id, created_at) VALUES (?, ?, ?, ?)";
        DatabaseUtils.executeInsert(sql, userId, bookingCode, appointmentId, LocalDateTime.now().format(DATETIME_FORMATTER));
    }

    public static List<String> getBookingCodesByUser(Long userId) {
        String sql = "SELECT booking_code FROM booking_codes WHERE user_id = ? ORDER BY created_at DESC";
        return DatabaseUtils.executeQueryList(sql, rs -> rs.getString("booking_code"), userId);
    }

    public static void updateAppointmentBookingCode(Long appointmentId, String bookingCode) {
        String sql = "UPDATE appointments SET booking_code = ? WHERE id = ?";
        DatabaseUtils.executeUpdate(sql, bookingCode, appointmentId);
    }
    
    // ============ Services ============
    
    /**
     * Получить все услуги (заглушка - возвращает пустой список)
     * В будущем можно добавить таблицу services в БД
     */
    public static List<lashes.bot.models.Service> getAllServices() {
        // Пока возвращаем пустой список
        // InlineKeyboardFactory использует дефолтные услуги если список пустой
        return new ArrayList<>();
    }
    
    /**
     * Получить услугу по ID (заглушка)
     */
    public static Optional<lashes.bot.models.Service> getServiceById(Long id) {
        return Optional.empty();
    }
    
    /**
     * Получить все записи пользователя
     */
    public static List<Appointment> getAllAppointmentsByUser(Long userId) {
        String sql = "SELECT * FROM appointments WHERE user_id = ? ORDER BY date DESC, time DESC";
        return DatabaseUtils.executeQueryList(sql, rs -> mapAppointment(rs), userId);
    }
    
    /**
     * Удалить историю записей пользователя
     */
    public static void deleteAppointmentHistory(Long userId) {
        String sql = "DELETE FROM appointments WHERE user_id = ?";
        DatabaseUtils.executeUpdate(sql, userId);
        log.info("Deleted appointment history for user {}", userId);
    }
}
