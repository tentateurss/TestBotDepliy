package lashes.bot.utils;

import lashes.bot.models.Appointment;
import lashes.bot.models.WorkDay;

import java.time.format.DateTimeFormatter;
import java.util.List;

public class ResponseFormatter {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    public static String formatAppointment(Appointment a) {
        return String.format("📅 %s в %s\n👤 %s\n📱 %s",
                a.getAppointmentTime().format(DATE_FORMAT),
                a.getAppointmentTime().format(TIME_FORMAT),
                a.getUserName(),
                a.getPhone()
        );
    }

    public static String formatAppointmentSimple(Appointment a) {
        return String.format("%s — %s (%s)",
                a.getAppointmentTime().format(TIME_FORMAT),
                a.getUserName(),
                a.getPhone()
        );
    }

    public static String formatAppointmentsList(List<Appointment> appointments, String dateStr) {
        if (appointments.isEmpty()) {
            return "📭 Нет записей";
        }

        StringBuilder sb = new StringBuilder("📋 *Записи на " + dateStr + ":*\n\n");
        for (int i = 0; i < appointments.size(); i++) {
            sb.append(i + 1).append(". ");
            sb.append(formatAppointmentSimple(appointments.get(i)));
            sb.append("\n");
        }
        return sb.toString();
    }

    public static String formatSchedule(WorkDay workDay, List<Appointment> appointments, String dateStr) {
        StringBuilder sb = new StringBuilder();
        sb.append("📅 *Расписание на ").append(dateStr).append("*\n\n");

        if (workDay == null || !workDay.isWorking()) {
            sb.append("🚫 *Выходной день*");
            return sb.toString();
        }

        sb.append("*Занятые слоты:*\n");
        if (appointments.isEmpty()) {
            sb.append("— Нет записей\n");
        } else {
            for (Appointment a : appointments) {
                sb.append("• ").append(formatAppointmentSimple(a)).append("\n");
            }
        }

        return sb.toString();
    }

    public static String formatBookingConfirmation(String name, String phone, String dateStr, String timeStr) {
        return String.format(
                "📋 *Проверьте данные записи:*\n\n" +
                        "👤 Имя: *%s*\n" +
                        "📱 Телефон: `%s`\n" +
                        "📅 Дата: *%s*\n" +
                        "⏰ Время: *%s*\n\n" +
                        "Всё верно?",
                name, phone, dateStr, timeStr
        );
    }

    public static String formatBookingSuccess(String dateStr, String timeStr, String name, String phone) {
        return String.format(
                "✅ *Запись подтверждена!*\n\n" +
                        "📅 *%s* в *%s*\n" +
                        "👤 *%s*\n" +
                        "📱 *%s*\n\n" +
                        "📍 *Адрес:* ул. Примерная, д. 123\n\n" +
                        "💖 Ждём вас! За 24 часа придёт напоминание.",
                dateStr, timeStr, name, phone
        );
    }

    public static String formatCancelConfirmation(String dateStr, String timeStr) {
        return String.format(
                "⚠️ *Вы уверены, что хотите отменить запись?*\n\n📅 %s в %s",
                dateStr, timeStr
        );
    }

    /**
     * Уведомление администратора о новой записи.
     * Показываем @username если есть, иначе user_id
     */
    public static String formatAdminNotification(String name, String phone, String dateStr, String timeStr,
                                                  Long userId, String telegramUsername) {
        String userRef;
        if (telegramUsername != null && !telegramUsername.isBlank()) {
            userRef = "@" + telegramUsername;
        } else {
            userRef = "ID: " + userId;
        }

        return String.format(
                "🆕 *НОВАЯ ЗАПИСЬ*\n\n" +
                        "👤 Клиент: %s\n" +
                        "📱 Телефон: `%s`\n" +
                        "📅 Дата: %s\n" +
                        "⏰ Время: %s\n" +
                        "💬 Telegram: %s",
                name, phone, dateStr, timeStr, userRef
        );
    }

    public static String formatCancelNotification(String name, String dateStr, String timeStr) {
        return String.format(
                "❌ *ОТМЕНА ЗАПИСИ*\n\n👤 Клиент: %s\n📅 Дата: %s\n⏰ Время: %s",
                name, dateStr, timeStr
        );
    }
}
