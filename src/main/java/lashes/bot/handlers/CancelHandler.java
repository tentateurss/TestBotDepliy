package lashes.bot.handlers;

import lashes.LashesBot;
import lashes.bot.config.BotConfig;
import lashes.bot.constants.Constants;
import lashes.bot.database.DatabaseService;
import lashes.bot.keyboards.InlineKeyboardFactory;
import lashes.bot.models.Appointment;
import lashes.bot.states.BotStateManager;
import lashes.bot.states.UserState;
import lashes.bot.utils.ReminderScheduler;
import lashes.bot.utils.ResponseFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.time.format.DateTimeFormatter;
import java.util.Optional;

public class CancelHandler {
    private static final Logger log = LoggerFactory.getLogger(CancelHandler.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern(Constants.DATE_PATTERN);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern(Constants.TIME_PATTERN);

    public void handleCancel(Message msg, LashesBot bot) {
        Long chatId = msg.getChatId();
        Long userId = msg.getFrom().getId();

        Optional<Appointment> apptOpt = DatabaseService.getActiveAppointmentByUser(userId);

        if (apptOpt.isEmpty()) {
            bot.sendMessage(chatId, "❌ У вас нет активных записей.");
            return;
        }

        Appointment appt = apptOpt.get();
        String dateStr = appt.getAppointmentTime().format(DATE_FORMATTER);
        String timeStr = appt.getAppointmentTime().format(TIME_FORMATTER);

        BotStateManager.setCancelAppointmentId(chatId, appt.getId());
        BotStateManager.setState(chatId, UserState.AWAITING_CANCEL_CONFIRMATION);

        String text = ResponseFormatter.formatCancelConfirmation(dateStr, timeStr);
        bot.sendMessage(chatId, text, "Markdown", InlineKeyboardFactory.getCancelConfirmationKeyboard());
    }

    public void handleConfirmCancel(CallbackQuery callback, LashesBot bot) {
        Long chatId = callback.getMessage().getChatId();
        Long userId = callback.getFrom().getId();

        Long appointmentId = BotStateManager.getCancelAppointmentId(chatId);

        if (appointmentId == null) {
            Optional<Appointment> apptOpt = DatabaseService.getActiveAppointmentByUser(userId);
            if (apptOpt.isPresent()) {
                appointmentId = apptOpt.get().getId();
            }
        }

        if (appointmentId == null) {
            bot.editMessageText("❌ Запись не найдена", chatId, callback.getMessage().getMessageId());
            BotStateManager.clear(chatId);
            return;
        }

        Optional<Appointment> apptOpt = DatabaseService.getAppointment(appointmentId);
        if (apptOpt.isEmpty()) {
            bot.editMessageText("❌ Запись не найдена", chatId, callback.getMessage().getMessageId());
            BotStateManager.clear(chatId);
            return;
        }

        Appointment appt = apptOpt.get();
        
        // Отправляем запрос на отмену админу
        String adminText = String.format(
            "⚠️ *ЗАПРОС НА ОТМЕНУ ЗАПИСИ*\n\n" +
            "👤 Клиент: %s\n" +
            "📱 Телефон: %s\n" +
            "📅 Дата: %s\n" +
            "⏰ Время: %s\n" +
            "💎 Процедура: %s\n\n" +
            "Подтвердите отмену записи:",
            appt.getUserName(),
            appt.getPhone(),
            appt.getAppointmentTime().format(DATE_FORMATTER),
            appt.getAppointmentTime().format(TIME_FORMATTER),
            appt.getServiceName() != null ? appt.getServiceName() : "Не указана"
        );

        for (Long adminId : BotConfig.getAdminIds()) {
            bot.sendMessage(adminId, adminText, "Markdown",
                InlineKeyboardFactory.getCancelRequestConfirmationKeyboard(appointmentId));
        }

        bot.editMessageText(
            "📨 Запрос на отмену отправлен администратору.\n\n" +
            "Ожидайте подтверждения. Это может занять несколько минут.",
            chatId, 
            callback.getMessage().getMessageId());
        
        BotStateManager.clear(chatId);
    }

    public void handleAdminConfirmCancel(CallbackQuery callback, LashesBot bot, Long appointmentId) {
        Long chatId = callback.getMessage().getChatId();

        log.info("=== АДМИН ПОДТВЕРЖДАЕТ ОТМЕНУ ===");
        log.info("Appointment ID: {}", appointmentId);

        Optional<Appointment> apptOpt = DatabaseService.getAppointment(appointmentId);
        if (apptOpt.isEmpty()) {
            bot.editMessageText("❌ Запись не найдена", chatId, callback.getMessage().getMessageId());
            bot.answerCallback(callback.getId(), "Запись не найдена", true);
            return;
        }

        Appointment appt = apptOpt.get();

        // Отменяем напоминания
        if (appt.getReminderJobKey() != null) {
            ReminderScheduler.cancelReminder(appt.getReminderJobKey());
        }
        if (appt.getReminder3hJobKey() != null) {
            ReminderScheduler.cancelReminder(appt.getReminder3hJobKey());
        }

        // Отменяем запись
        DatabaseService.cancelAppointment(appointmentId);

        bot.editMessageText(
            "✅ *ОТМЕНА ПОДТВЕРЖДЕНА*\n\n" +
            "Запись отменена.",
            chatId,
            callback.getMessage().getMessageId(),
            "Markdown");

        // Уведомляем клиента
        String clientMessage = String.format(
            "✅ Ваша запись отменена\n\n" +
            "Администратор подтвердил отмену записи:\n" +
            "📅 %s в %s\n\n" +
            "Будем рады видеть вас в другой раз! 💖",
            appt.getAppointmentTime().format(DATE_FORMATTER),
            appt.getAppointmentTime().format(TIME_FORMATTER)
        );
        bot.sendMessage(appt.getUserId(), clientMessage);

        // Обновляем главное меню (убираем кнопку отмены)
        boolean isAdmin = BotConfig.isAdmin(appt.getUserId());
        bot.sendMessage(appt.getUserId(), "Выберите действие:", null,
                lashes.bot.keyboards.ReplyKeyboardFactory.getMainMenu(isAdmin, appt.getUserId()));

        bot.answerCallback(callback.getId(), "✅ Отмена подтверждена", false);
        
        log.info("Appointment {} cancelled by admin", appointmentId);
    }

    public void handleAdminRejectCancel(CallbackQuery callback, LashesBot bot, Long appointmentId) {
        Long chatId = callback.getMessage().getChatId();

        log.info("=== АДМИН ОТКЛОНЯЕТ ОТМЕНУ ===");
        log.info("Appointment ID: {}", appointmentId);

        Optional<Appointment> apptOpt = DatabaseService.getAppointment(appointmentId);
        if (apptOpt.isEmpty()) {
            bot.editMessageText("❌ Запись не найдена", chatId, callback.getMessage().getMessageId());
            bot.answerCallback(callback.getId(), "Запись не найдена", true);
            return;
        }

        Appointment appt = apptOpt.get();

        bot.editMessageText(
            "❌ *ОТМЕНА ОТКЛОНЕНА*\n\n" +
            "Запрос на отмену отклонён.",
            chatId,
            callback.getMessage().getMessageId(),
            "Markdown");

        // Уведомляем клиента
        String clientMessage = String.format(
            "❌ Отмена записи отклонена\n\n" +
            "Администратор не подтвердил отмену записи:\n" +
            "📅 %s в %s\n\n" +
            "Ваша запись остаётся активной.\n" +
            "Для отмены свяжитесь с администратором напрямую.",
            appt.getAppointmentTime().format(DATE_FORMATTER),
            appt.getAppointmentTime().format(TIME_FORMATTER)
        );
        bot.sendMessage(appt.getUserId(), clientMessage);

        bot.answerCallback(callback.getId(), "❌ Отмена отклонена", false);
        
        log.info("Cancellation request for appointment {} rejected by admin", appointmentId);
    }
}