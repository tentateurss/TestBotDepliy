package lashes.bot.handlers;

import lashes.LashesBot;
import lashes.bot.config.BotConfig;
import lashes.bot.constants.Constants;
import lashes.bot.database.DatabaseService;
import lashes.bot.keyboards.InlineKeyboardFactory;
import lashes.bot.models.Appointment;
import lashes.bot.states.BotStateManager;
import lashes.bot.states.UserState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class StatisticsHandler {
    private static final Logger log = LoggerFactory.getLogger(StatisticsHandler.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    public void handleStatisticsMenu(CallbackQuery callback, LashesBot bot) {
        Long chatId = callback.getMessage().getChatId();
        int msgId = callback.getMessage().getMessageId();
        Long userId = callback.getFrom().getId();

        if (!BotConfig.isAdmin(userId)) {
            bot.answerCallback(callback.getId(), "Доступ запрещен", true);
            return;
        }

        YearMonth currentMonth = YearMonth.now();
        showStatistics(bot, chatId, msgId, currentMonth.getYear(), currentMonth.getMonthValue());
    }

    public void handleStatisticsNav(CallbackQuery callback, LashesBot bot, String data) {
        Long chatId = callback.getMessage().getChatId();
        int msgId = callback.getMessage().getMessageId();

        String[] parts = data.split(":");
        if (parts.length != 3) return;

        int year = Integer.parseInt(parts[1]);
        int month = Integer.parseInt(parts[2]);

        showStatistics(bot, chatId, msgId, year, month);
    }

    private void showStatistics(LashesBot bot, Long chatId, int msgId, int year, int month) {
        Map<String, Object> stats = DatabaseService.getMonthlyStatistics(year, month);
        
        int totalAppointments = (int) stats.get("totalAppointments");
        double totalRevenue = (double) stats.get("totalRevenue");
        List<Map<String, Object>> popularSlots = (List<Map<String, Object>>) stats.get("popularSlots");

        StringBuilder message = new StringBuilder();
        message.append("📊 *СТАТИСТИКА*\n\n");
        message.append(String.format("📅 Месяц: *%02d.%04d*\n\n", month, year));
        message.append(String.format("📝 Всего записей: *%d*\n", totalAppointments));
        message.append(String.format("💰 Доход: *%.2f ₽*\n\n", totalRevenue));

        if (!popularSlots.isEmpty()) {
            message.append("⭐️ *Популярные слоты:*\n");
            for (Map<String, Object> slot : popularSlots) {
                message.append(String.format("• %s — %d записей\n", 
                    slot.get("time"), slot.get("count")));
            }
        } else {
            message.append("_Нет данных о популярных слотах_\n");
        }

        bot.editMessageText(message.toString(), chatId, msgId, "Markdown");
        bot.editMessageReplyMarkup(InlineKeyboardFactory.createStatisticsNavigation(year, month), chatId, msgId);
    }

    public void handleReviewsMenu(CallbackQuery callback, LashesBot bot) {
        Long chatId = callback.getMessage().getChatId();
        int msgId = callback.getMessage().getMessageId();
        Long userId = callback.getFrom().getId();

        if (!BotConfig.isAdmin(userId)) {
            bot.answerCallback(callback.getId(), "Доступ запрещен", true);
            return;
        }

        List<Map<String, Object>> reviews = DatabaseService.getAllReviews();

        StringBuilder message = new StringBuilder();
        message.append("⭐️ *ОТЗЫВЫ КЛИЕНТОВ*\n\n");

        if (reviews.isEmpty()) {
            message.append("_Пока нет отзывов_");
        } else {
            int count = 0;
            for (Map<String, Object> review : reviews) {
                if (count >= 10) break; // Показываем последние 10
                
                String userName = (String) review.get("userName");
                int rating = (int) review.get("rating");
                String comment = (String) review.get("comment");
                LocalDateTime createdAt = (LocalDateTime) review.get("createdAt");

                message.append(String.format("👤 *%s*\n", userName));
                message.append(getStars(rating)).append("\n");
                if (comment != null && !comment.isEmpty()) {
                    message.append(String.format("💬 _%s_\n", comment));
                }
                message.append(String.format("📅 %s\n\n", createdAt.format(DATE_FORMATTER)));
                count++;
            }
        }

        bot.editMessageText(message.toString(), chatId, msgId, "Markdown");
        bot.editMessageReplyMarkup(InlineKeyboardFactory.createBackToAdminButton(), chatId, msgId);
    }

    private String getStars(int rating) {
        StringBuilder stars = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            stars.append(i < rating ? "⭐️" : "☆");
        }
        return stars.toString();
    }

    public void handleClientHistory(Message msg, LashesBot bot) {
        Long chatId = msg.getChatId();
        Long userId = msg.getFrom().getId();

        List<Appointment> appointments = DatabaseService.getAppointmentsByUser(userId);

        StringBuilder message = new StringBuilder();
        message.append("📋 *ИСТОРИЯ ВАШИХ ЗАПИСЕЙ*\n\n");

        if (appointments.isEmpty()) {
            message.append("_У вас пока нет записей_");
        } else {
            for (Appointment appt : appointments) {
                LocalDateTime time = appt.getAppointmentTime();
                message.append(String.format("📅 %s в %s\n", 
                    time.toLocalDate().format(DATE_FORMATTER),
                    time.toLocalTime().format(TIME_FORMATTER)));
                message.append(String.format("📞 %s\n", appt.getPhone()));
                
                String statusEmoji = "active".equals(appt.getStatus()) ? "✅" : "❌";
                String statusText = "active".equals(appt.getStatus()) ? "Активна" : "Отменена";
                message.append(String.format("%s Статус: %s\n\n", statusEmoji, statusText));
            }
        }

        bot.sendMessage(chatId, message.toString(), "Markdown");
    }

    public void handleBroadcastMenu(CallbackQuery callback, LashesBot bot) {
        Long chatId = callback.getMessage().getChatId();
        int msgId = callback.getMessage().getMessageId();
        Long userId = callback.getFrom().getId();

        if (!BotConfig.isAdmin(userId)) {
            bot.answerCallback(callback.getId(), "Доступ запрещен", true);
            return;
        }

        BotStateManager.setState(chatId, UserState.ADMIN_BROADCAST);
        bot.editMessageText(
            "📢 *РАССЫЛКА*\n\n" +
            "Введите сообщение, которое будет отправлено всем клиентам с активными записями:",
            chatId, msgId, "Markdown");
        bot.editMessageReplyMarkup(null, chatId, msgId);
    }

    public void handleBroadcastMessage(Message msg, LashesBot bot) {
        Long chatId = msg.getChatId();
        String text = msg.getText();

        List<Long> userIds = DatabaseService.getAllUserIdsWithActiveAppointments();
        
        int successCount = 0;
        int failCount = 0;

        for (Long userId : userIds) {
            try {
                bot.sendMessage(userId, text, "Markdown");
                successCount++;
                Thread.sleep(100); // Небольшая задержка между сообщениями
            } catch (Exception e) {
                failCount++;
                log.error("Failed to send broadcast to user {}", userId, e);
            }
        }

        BotStateManager.clear(chatId);
        bot.sendMessage(chatId, 
            String.format("✅ Рассылка завершена!\n\n" +
                "Отправлено: %d\n" +
                "Ошибок: %d", successCount, failCount));
    }
}
